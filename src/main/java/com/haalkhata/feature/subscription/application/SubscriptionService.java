package com.haalkhata.feature.subscription.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.subscription.domain.PaymentTransaction;
import com.haalkhata.feature.subscription.domain.Subscription;
import com.haalkhata.feature.subscription.domain.SubscriptionPlan;
import com.haalkhata.feature.subscription.infrastructure.PaymentTransactionRepository;
import com.haalkhata.feature.subscription.infrastructure.SubscriptionPlanRepository;
import com.haalkhata.feature.subscription.infrastructure.SubscriptionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    public static final String SUB_PENDING = "PENDING";
    public static final String SUB_ACTIVE = "ACTIVE";
    public static final String SUB_EXPIRED = "EXPIRED";
    public static final String SUB_CANCELLED = "CANCELLED";
    public static final String SUB_FAILED = "FAILED";

    public static final String PLAN_TRIAL = "TRIAL";
    public static final int TRIAL_DAYS_DEFAULT = 30;

    public static final String TX_INITIATED = "INITIATED";
    public static final String TX_PENDING = "PENDING";
    public static final String TX_SUCCESS = "SUCCESS";
    public static final String TX_FAILED = "FAILED";
    public static final String TX_CANCELLED = "CANCELLED";
    public static final String TX_EXPIRED = "EXPIRED";

    public record PurchaseRequest(String planCode) {
    }

    public record ConfirmRequest(String paymentId) {
    }

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentGateway paymentGateway;

    public List<Map<String, Object>> plans() {
        return planRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(this::planToJson).toList();
    }

    @Transactional
    public Map<String, Object> purchase(CurrentUser user, PurchaseRequest request) {
        SubscriptionPlan plan = loadActivePlan(request.planCode());
        BigDecimal amount = plan.getPriceBdt().setScale(2, RoundingMode.HALF_UP);

        Optional<Subscription> pending = subscriptionRepository
                .findTopByShopIdAndStatusAndPlanCodeOrderByEndDateDesc(
                        user.getShopId(), SUB_PENDING, plan.getPlanCode());
        if (pending.isPresent()) {
            Subscription sub = pending.get();
            log.info("SUBSCRIPTION_PURCHASE_REUSE shopId={} subscriptionId={}",
                    user.getShopId(), sub.getSubscriptionId());
            Optional<PaymentTransaction> persisted = transactionRepository
                    .findByInternalTransactionId(sub.getInternalTransactionId());
            if (persisted.isPresent() && TX_PENDING.equals(persisted.get().getStatus())
                    && persisted.get().getGatewayPaymentId() != null) {
                PaymentTransaction tx = persisted.get();
                boolean alreadyPaid = false;
                try {
                    PaymentGateway.QueryPayment old =
                            paymentGateway.queryPayment(tx.getGatewayPaymentId());
                    alreadyPaid = "Completed".equalsIgnoreCase(old.transactionStatus());
                } catch (BusinessException ex) {
                    // Stale/expired paymentID at the gateway — safe to generate a fresh one.
                    log.info("SUBSCRIPTION_QUERY_STALE paymentId={} code={} -> recreating",
                            tx.getGatewayPaymentId(), ex.getCode());
                } catch (RestClientException ex) {
                    // Gateway is rate-limiting or unreachable — the old payment is not
                    // confirmed, so generate a fresh payment instead of erroring out.
                    log.info("SUBSCRIPTION_QUERY_UNAVAILABLE paymentId={} cause={} -> recreating",
                            tx.getGatewayPaymentId(), ex.getClass().getSimpleName());
                }
                if (alreadyPaid) {
                    log.info("SUBSCRIPTION_PURCHASE_REUSED_PAID subscriptionId={} paymentId={}",
                            sub.getSubscriptionId(), tx.getGatewayPaymentId());
                    return confirmTransaction(tx);
                }
                log.info("SUBSCRIPTION_PURCHASE_RECREATE_PAYMENT subscriptionId={} oldPaymentId={}",
                        sub.getSubscriptionId(), tx.getGatewayPaymentId());
                return restartPayment(sub, tx);
            }
            return purchaseResponse(sub, sub.getInternalTransactionId());
        }

        LocalDateTime now = LocalDateTime.now();
        String internalTxId = generateInternalTransactionId();
        Subscription subscription = Subscription.builder()
                .subscriptionId(Ids.uuid())
                .shopId(user.getShopId())
                .planCode(plan.getPlanCode())
                .status(SUB_PENDING)
                .amountBdt(amount)
                .startDate(null)
                .endDate(null)
                .internalTransactionId(internalTxId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        subscriptionRepository.save(subscription);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .paymentId(Ids.uuid())
                .subscriptionId(subscription.getSubscriptionId())
                .shopId(user.getShopId())
                .internalTransactionId(internalTxId)
                .gateway(paymentGateway.name())
                .amountBdt(amount)
                .status(TX_INITIATED)
                .payerReference(user.getShopId())
                .merchantInvoiceNumber(internalTxId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        transactionRepository.save(transaction);

        PaymentGateway.CreatePayment created;
        try {
            created = paymentGateway.createPayment(amount, internalTxId, user.getShopId());
        } catch (BusinessException ex) {
            transaction.setStatus(TX_FAILED);
            transaction.setErrorCode(ex.getCode());
            transaction.setErrorMessage(truncate(ex.getMessage()));
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            subscription.setStatus(SUB_FAILED);
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
            log.warn("SUBSCRIPTION_BKASH_CREATE_FAILED shopId={} subscriptionId={} code={}",
                    user.getShopId(), subscription.getSubscriptionId(), ex.getCode());
            throw ex;
        } catch (RestClientException ex) {
            transaction.setStatus(TX_FAILED);
            transaction.setErrorCode("BKASH_GATEWAY_UNAVAILABLE");
            transaction.setErrorMessage("bKash payment gateway is busy, please try again later");
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            subscription.setStatus(SUB_FAILED);
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
            log.warn("SUBSCRIPTION_BKASH_GATEWAY_UNAVAILABLE shopId={} subscriptionId={} cause={}",
                    user.getShopId(), subscription.getSubscriptionId(), ex.getClass().getSimpleName());
            throw new BusinessException("BKASH_GATEWAY_UNAVAILABLE",
                    "bKash payment gateway is busy, please try again later", HttpStatus.SERVICE_UNAVAILABLE);
        }

        transaction.setGatewayPaymentId(created.paymentId());
        transaction.setPaymentCreateTime(LocalDateTime.now());
        transaction.setStatus(TX_PENDING);
        transaction.setBillingType(created.billingType());
        transaction.setBkashUrl(created.bkashUrl());
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("SUBSCRIPTION_BKASH_CREATE_OK shopId={} subscriptionId={} plan={} amount={} paymentId={}",
                user.getShopId(), subscription.getSubscriptionId(), plan.getPlanCode(),
                amount, created.paymentId());
        return purchaseResponse(subscription, internalTxId);
    }

    @Transactional
    public Map<String, Object> confirm(CurrentUser user, ConfirmRequest request) {
        PaymentTransaction transaction = transactionRepository
                .findByPaymentIdAndShopId(request.paymentId(), user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Payment not found for this shop"));
        return confirmTransaction(transaction);
    }

    @Transactional
    public Map<String, Object> confirmFromCallback(String gatewayPaymentId) {
        if (gatewayPaymentId == null || gatewayPaymentId.isBlank()) {
            throw BusinessException.badRequest("paymentID is required");
        }
        PaymentTransaction transaction = transactionRepository
                .findByGatewayPaymentId(gatewayPaymentId)
                .orElseThrow(() -> BusinessException.notFound("Unknown paymentID: " + gatewayPaymentId));
        return confirmTransaction(transaction);
    }

    private Map<String, Object> confirmTransaction(PaymentTransaction transaction) {
        if (TX_SUCCESS.equals(transaction.getStatus())) {
            Subscription sub = subscriptionRepository.findById(transaction.getSubscriptionId()).orElseThrow();
            log.info("PAYMENT_ALREADY_PROCESSED paymentId={} status=SUCCESS", transaction.getPaymentId());
            return confirmResponse(transaction, sub);
        }
        if (List.of(TX_FAILED, TX_CANCELLED, TX_EXPIRED).contains(transaction.getStatus())) {
            Subscription sub = subscriptionRepository.findById(transaction.getSubscriptionId()).orElseThrow();
            log.info("PAYMENT_ALREADY_PROCESSED paymentId={} status={}", transaction.getPaymentId(),
                    transaction.getStatus());
            return confirmResponse(transaction, sub);
        }
        if (transaction.getGatewayPaymentId() == null || transaction.getGatewayPaymentId().isBlank()) {
            throw BusinessException.badRequest("Payment was not initialized at the gateway");
        }

        PaymentGateway.ExecutePayment executed;
        try {
            executed = paymentGateway.executePayment(transaction.getGatewayPaymentId());
        } catch (BusinessException ex) {
            transaction.setErrorCode(ex.getCode());
            transaction.setErrorMessage(truncate(ex.getMessage()));
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            log.warn("SUBSCRIPTION_BKASH_EXECUTE_FAILED paymentId={} code={}",
                    transaction.getPaymentId(), ex.getCode());
            throw ex;
        }

        LocalDateTime now = LocalDateTime.now();
        transaction.setExecuteTime(now);
        transaction.setGatewayTransactionId(executed.trxId());
        transaction.setUpdatedAt(now);
        transactionRepository.save(transaction);

        boolean completed = "Completed".equalsIgnoreCase(executed.transactionStatus());
        if (!completed) {
            // Non-completed means the customer cancelled, closed the checkout in the
            // middle, or the payment expired — never keep it dangling as "pending".
            transaction.setStatus(TX_CANCELLED);
            transaction.setUpdatedAt(now);
            transactionRepository.save(transaction);
            Subscription sub = subscriptionRepository.findById(transaction.getSubscriptionId()).orElseThrow();
            sub.setStatus(SUB_CANCELLED);
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
            log.info("SUBSCRIPTION_CANCELLED paymentId={} bKashStatus={}", transaction.getPaymentId(),
                    executed.transactionStatus());
            return confirmResponse(transaction, sub);
        }

        BigDecimal paid = parsedBdtAmount(executed.amount());
        if (paid == null || paid.compareTo(transaction.getAmountBdt()) != 0) {
            transaction.setStatus(TX_FAILED);
            transaction.setErrorCode("PAYMENT_AMOUNT_MISMATCH");
            transaction.setErrorMessage("Gateway amount " + paid + " does not match " + transaction.getAmountBdt());
            transaction.setUpdatedAt(now);
            transactionRepository.save(transaction);
            Subscription sub = subscriptionRepository.findById(transaction.getSubscriptionId()).orElseThrow();
            sub.setStatus(SUB_FAILED);
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
            log.error("SUBSCRIPTION_AMOUNT_MISMATCH paymentId={} expected={} got={}",
                    transaction.getPaymentId(), transaction.getAmountBdt(), paid);
            throw new BusinessException("PAYMENT_AMOUNT_MISMATCH",
                    "Gateway returned an amount that does not match the subscription amount", HttpStatus.CONFLICT);
        }

        transaction.setStatus(TX_SUCCESS);
        transaction.setPaidTime(now);
        transaction.setUpdatedAt(now);
        transactionRepository.save(transaction);
        log.info("PAYMENT_SUCCEEDED paymentId={} trxId={} amount={}",
                transaction.getPaymentId(), executed.trxId(), transaction.getAmountBdt());

        Subscription sub = activateSubscription(transaction);
        return confirmResponse(transaction, sub);
    }

    private Subscription activateSubscription(PaymentTransaction transaction) {
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = subscriptionRepository.findById(transaction.getSubscriptionId()).orElseThrow();
        SubscriptionPlan plan = planRepository.findById(subscription.getPlanCode()).orElseThrow();

        LocalDateTime start = now;
        Optional<Subscription> currentActive = subscriptionRepository
                .findTopByShopIdAndStatusOrderByEndDateDesc(subscription.getShopId(), SUB_ACTIVE);
        if (currentActive.isPresent()
                && currentActive.get().getEndDate() != null
                && currentActive.get().getEndDate().isAfter(now)) {
            start = currentActive.get().getEndDate();
        }
        LocalDateTime end = start.plusDays(plan.getDurationDays());
        subscription.setStatus(SUB_ACTIVE);
        subscription.setStartDate(start);
        subscription.setEndDate(end);
        subscription.setUpdatedAt(now);
        subscriptionRepository.save(subscription);
        log.info("SUBSCRIPTION_ACTIVATED subscriptionId={} shopId={} plan={} start={} end={}",
                subscription.getSubscriptionId(), subscription.getShopId(), plan.getPlanCode(), start, end);
        return subscription;
    }

    /**
     * Grants the free trial subscription for a newly registered shop.
     * The trial length comes from subscription_plan.trial_days (plan 'TRIAL'),
     * so it stays configurable in the DB without any code change or restart.
     * Idempotent: a shop never receives more than one trial — not even after
     * the first trial has expired.
     */
    @Transactional
    public void grantTrial(String shopId) {
        if (subscriptionRepository.findByShopIdOrderByCreatedAtDesc(shopId).stream()
                .anyMatch(sub -> PLAN_TRIAL.equals(sub.getPlanCode()))) {
            log.info("TRIAL_ALREADY_GRANTED shopId={}", shopId);
            return;
        }
        int trialDays = planRepository.findById(PLAN_TRIAL)
                .map(SubscriptionPlan::getTrialDays)
                .filter(days -> days != null && days > 0)
                .orElse(TRIAL_DAYS_DEFAULT);
        if (trialDays <= 0) {
            log.info("TRIAL_DISABLED shopId={} trialDays={}", shopId, trialDays);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Subscription trial = Subscription.builder()
                .subscriptionId(Ids.uuid())
                .shopId(shopId)
                .planCode(PLAN_TRIAL)
                .status(SUB_ACTIVE)
                .amountBdt(BigDecimal.ZERO)
                .startDate(now)
                .endDate(now.plusDays(trialDays))
                .internalTransactionId("TRIAL-" + Ids.uuid())
                .createdAt(now)
                .updatedAt(now)
                .build();
        subscriptionRepository.save(trial);
        log.info("TRIAL_GRANTED shopId={} trialDays={} end={}", shopId, trialDays, trial.getEndDate());
    }

    @Transactional
    public Map<String, Object> status(CurrentUser user) {
        // Lazy first-contact trial: a brand-new shop that has never touched
        // the subscription system gets its free trial right here — no changes
        // needed in the shared auth service.
        List<Subscription> existing = subscriptionRepository
                .findByShopIdOrderByCreatedAtDesc(user.getShopId());
        if (existing.isEmpty()) {
            grantTrial(user.getShopId());
            existing = subscriptionRepository
                    .findByShopIdOrderByCreatedAtDesc(user.getShopId());
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<Subscription> active = existing.stream()
                .filter(sub -> SUB_ACTIVE.equals(sub.getStatus()))
                .filter(sub -> sub.getEndDate() == null || sub.getEndDate().isAfter(now))
                .findFirst();
        Optional<Subscription> latest = existing.stream().findFirst();

        Map<String, Object> json = new HashMap<>();
        json.put("hasActive", active.isPresent());
        if (active.isPresent()) {
            Subscription sub = active.get();
            boolean isTrial = PLAN_TRIAL.equals(sub.getPlanCode());
            long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(now, sub.getEndDate()));
            Map<String, Object> subJson = subscriptionToJson(sub);
            subJson.put("isTrial", isTrial);
            subJson.put("plan", planToJson(planRepository.findById(sub.getPlanCode()).orElse(null)));
            json.put("subscription", subJson);
            json.put("daysRemaining", daysRemaining);
            json.put("isTrial", isTrial);
        }
        latest.ifPresent(sub -> json.put("latest", subscriptionToJson(sub)));
        return json;
    }

    public Map<String, Object> details(String subscriptionId, CurrentUser user) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .filter(s -> s.getShopId().equals(user.getShopId()))
                .orElseThrow(() -> BusinessException.notFound("Subscription not found"));
        Map<String, Object> json = subscriptionToJson(sub);
        json.put("plan", planToJson(planRepository.findById(sub.getPlanCode()).orElse(null)));
        return json;
    }

    private SubscriptionPlan loadActivePlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            throw BusinessException.badRequest("planCode is required");
        }
        SubscriptionPlan plan = planRepository.findById(planCode.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("SUBSCRIPTION_PLAN_NOT_FOUND",
                        "Subscription plan " + planCode + " not found", HttpStatus.NOT_FOUND));
        if (!plan.isActive()) {
            throw new BusinessException("SUBSCRIPTION_PLAN_INACTIVE",
                    "Subscription plan " + planCode + " is not available");
        }
        return plan;
    }

    private Map<String, Object> restartPayment(Subscription subscription, PaymentTransaction tx) {
        // Always derive the invoice from the constant internal transaction id so
        // repeated retries never grow past the 64-char column limit.
        String invoiceNo = subscription.getInternalTransactionId() + "-"
                + Long.toUnsignedString(System.nanoTime(), 36).toUpperCase();
        PaymentGateway.CreatePayment created;
        try {
            created = paymentGateway.createPayment(subscription.getAmountBdt(), invoiceNo, subscription.getShopId());
        } catch (BusinessException ex) {
            tx.setErrorCode(ex.getCode());
            tx.setErrorMessage(truncate(ex.getMessage()));
            tx.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(tx);
            log.warn("SUBSCRIPTION_BKASH_CREATE_FAILED paymentId={} code={}",
                    tx.getPaymentId(), ex.getCode());
            throw ex;
        } catch (RestClientException ex) {
            tx.setErrorCode("BKASH_GATEWAY_UNAVAILABLE");
            tx.setErrorMessage("bKash payment gateway is busy, please try again later");
            tx.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(tx);
            log.warn("SUBSCRIPTION_BKASH_GATEWAY_UNAVAILABLE paymentId={} cause={}",
                    tx.getPaymentId(), ex.getClass().getSimpleName());
            throw new BusinessException("BKASH_GATEWAY_UNAVAILABLE",
                    "bKash payment gateway is busy, please try again later", HttpStatus.SERVICE_UNAVAILABLE);
        }
        tx.setGatewayPaymentId(created.paymentId());
        tx.setMerchantInvoiceNumber(invoiceNo);
        tx.setPaymentCreateTime(LocalDateTime.now());
        tx.setStatus(TX_PENDING);
        tx.setBillingType(created.billingType());
        tx.setBkashUrl(created.bkashUrl());
        tx.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(tx);
        log.info("SUBSCRIPTION_BKASH_CREATE_RECREATED shopId={} subscriptionId={} plan={} amount={} paymentId={}",
                subscription.getShopId(), subscription.getSubscriptionId(),
                subscription.getPlanCode(), subscription.getAmountBdt(), created.paymentId());
        return purchaseResponse(subscription, subscription.getInternalTransactionId());
    }

    private Map<String, Object> purchaseResponse(Subscription subscription, String internalTxId) {
        PaymentTransaction transaction = transactionRepository
                .findByInternalTransactionId(internalTxId).orElseThrow();
        Map<String, Object> json = new HashMap<>();
        json.put("subscriptionId", subscription.getSubscriptionId());
        json.put("paymentId", transaction.getPaymentId());
        json.put("paymentStatus", transaction.getStatus());
        json.put("planCode", subscription.getPlanCode());
        json.put("amount", money(subscription.getAmountBdt()));
        json.put("gateway", transaction.getGateway());
        json.put("gatewayPaymentId", transaction.getGatewayPaymentId());
        json.put("payerReference", transaction.getPayerReference());
        json.put("merchantInvoiceNumber", transaction.getMerchantInvoiceNumber());
        json.put("bkashUrl", transaction.getBkashUrl());
        json.put("currency", "BDT");
        return json;
    }

    private Map<String, Object> confirmResponse(PaymentTransaction transaction, Subscription subscription) {
        Map<String, Object> json = new HashMap<>();
        json.put("paymentId", transaction.getPaymentId());
        json.put("paymentStatus", transaction.getStatus());
        json.put("gatewayTransactionId", transaction.getGatewayTransactionId());
        json.put("amount", money(transaction.getAmountBdt()));
        json.put("subscriptionId", subscription.getSubscriptionId());
        json.put("subscriptionStatus", subscription.getStatus());
        json.put("planCode", subscription.getPlanCode());
        json.put("startDate", subscription.getStartDate());
        json.put("endDate", subscription.getEndDate());
        if (transaction.getErrorMessage() != null) {
            json.put("errorMessage", transaction.getErrorMessage());
        }
        return json;
    }

    private Map<String, Object> planToJson(SubscriptionPlan plan) {
        Map<String, Object> json = new HashMap<>();
        if (plan == null) {
            return json;
        }
        json.put("planCode", plan.getPlanCode());
        json.put("name", plan.getName());
        json.put("price", money(plan.getPriceBdt()));
        json.put("currency", "BDT");
        json.put("durationDays", plan.getDurationDays());
        json.put("trialDays", plan.getTrialDays());
        json.put("isActive", plan.isActive());
        return json;
    }

    private Map<String, Object> subscriptionToJson(Subscription sub) {
        Map<String, Object> json = new HashMap<>();
        json.put("subscriptionId", sub.getSubscriptionId());
        json.put("shopId", sub.getShopId());
        json.put("planCode", sub.getPlanCode());
        json.put("status", sub.getStatus());
        json.put("amountBdt", money(sub.getAmountBdt()));
        json.put("startDate", sub.getStartDate());
        json.put("endDate", sub.getEndDate());
        json.put("internalTransactionId", sub.getInternalTransactionId());
        json.put("createdAt", sub.getCreatedAt());
        json.put("updatedAt", sub.getUpdatedAt());
        return json;
    }

    private String generateInternalTransactionId() {
        String candidate;
        for (int attempt = 0; attempt < 5; attempt++) {
            candidate = "HK" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                    + Ids.randomCode(4);
            if (transactionRepository.findByInternalTransactionId(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "HK" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                + Ids.randomCode(8);
    }

    private static BigDecimal parsedBdtAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 300));
    }
}