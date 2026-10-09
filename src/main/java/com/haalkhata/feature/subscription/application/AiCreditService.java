package com.haalkhata.feature.subscription.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.subscription.domain.AiCreditBalance;
import com.haalkhata.feature.subscription.domain.AiCreditUsage;
import com.haalkhata.feature.subscription.infrastructure.AiCreditBalanceRepository;
import com.haalkhata.feature.subscription.infrastructure.AiCreditUsageRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic, server-side AI quota accounting. Client state is display-only. */
@Service
@RequiredArgsConstructor
public class AiCreditService {
    private final AiCreditBalanceRepository balances;
    private final AiCreditUsageRepository usages;
    private final SubscriptionService subscriptions;

    public record Reservation(String usageId) {}

    @Transactional
    public Reservation reserve(String shopId, String feature, int credits) {
        if (credits <= 0) throw new IllegalArgumentException("credits must be positive");
        AiCreditBalance balance = lockedBalance(shopId);
        refreshMonthlyAllowance(balance, shopId);
        int monthlyAvailable = Math.max(0, balance.getMonthlyCredits() - balance.getMonthlyCreditsUsed());
        int fromMonthly = Math.min(credits, monthlyAvailable);
        int fromWelcome = credits - fromMonthly;
        if (fromWelcome > balance.getWelcomeCreditsRemaining()) {
            throw new BusinessException("AI_QUOTA_EXCEEDED",
                    "AI credits are finished. Upgrade your plan or buy more credits.", HttpStatus.TOO_MANY_REQUESTS);
        }
        balance.setMonthlyCreditsUsed(balance.getMonthlyCreditsUsed() + fromMonthly);
        balance.setWelcomeCreditsRemaining(balance.getWelcomeCreditsRemaining() - fromWelcome);
        balance.setUpdatedAt(LocalDateTime.now());
        balances.save(balance);
        String usageId = Ids.uuid();
        usages.save(AiCreditUsage.builder().usageId(usageId).shopId(shopId).feature(feature)
                .monthlyCreditsUsed(fromMonthly).welcomeCreditsUsed(fromWelcome).refunded(false)
                .createdAt(LocalDateTime.now()).build());
        return new Reservation(usageId);
    }

    @Transactional
    public void refund(Reservation reservation) {
        AiCreditUsage usage = usages.findById(reservation.usageId()).orElse(null);
        if (usage == null || usage.isRefunded()) return;
        AiCreditBalance balance = lockedBalance(usage.getShopId());
        balance.setMonthlyCreditsUsed(Math.max(0, balance.getMonthlyCreditsUsed() - usage.getMonthlyCreditsUsed()));
        balance.setWelcomeCreditsRemaining(balance.getWelcomeCreditsRemaining() + usage.getWelcomeCreditsUsed());
        balance.setUpdatedAt(LocalDateTime.now());
        usage.setRefunded(true);
        usage.setRefundedAt(LocalDateTime.now());
        balances.save(balance);
        usages.save(usage);
    }

    @Transactional
    public Map<String, Object> status(String shopId) {
        AiCreditBalance balance = lockedBalance(shopId);
        refreshMonthlyAllowance(balance, shopId);
        balances.save(balance);
        int monthlyRemaining = Math.max(0, balance.getMonthlyCredits() - balance.getMonthlyCreditsUsed());
        return Map.of("monthlyLimit", balance.getMonthlyCredits(), "monthlyUsed", balance.getMonthlyCreditsUsed(),
                "monthlyRemaining", monthlyRemaining, "welcomeRemaining", balance.getWelcomeCreditsRemaining(),
                "remaining", monthlyRemaining + balance.getWelcomeCreditsRemaining(),
                "periodStart", balance.getPeriodStart(), "periodEnd", balance.getPeriodStart().plusMonths(1).minusDays(1));
    }

    private AiCreditBalance lockedBalance(String shopId) {
        return balances.findByShopIdForUpdate(shopId).orElseGet(() -> balances.saveAndFlush(AiCreditBalance.builder()
                .shopId(shopId).welcomeCreditsRemaining(subscriptions.aiWelcomeCredits())
                .periodStart(monthStart()).monthlyCredits(0).monthlyCreditsUsed(0)
                .updatedAt(LocalDateTime.now()).build()));
    }

    private void refreshMonthlyAllowance(AiCreditBalance balance, String shopId) {
        int allowance = subscriptions.aiCreditsPerMonth(shopId);
        LocalDate period = monthStart();
        if (!period.equals(balance.getPeriodStart())) {
            balance.setPeriodStart(period);
            balance.setMonthlyCredits(allowance);
            balance.setMonthlyCreditsUsed(0);
        } else if (allowance > balance.getMonthlyCredits()) {
            balance.setMonthlyCredits(allowance);
        }
    }

    private LocalDate monthStart() {
        return LocalDate.now(ZoneId.of("Asia/Dhaka")).with(TemporalAdjusters.firstDayOfMonth());
    }
}
