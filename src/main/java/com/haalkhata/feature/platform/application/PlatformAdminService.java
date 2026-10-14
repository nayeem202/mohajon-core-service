package com.haalkhata.feature.platform.application;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.shop.infrastructure.ShopRepository;
import com.haalkhata.feature.auth.infrastructure.AppUserRepository;
import com.haalkhata.feature.notification.application.NotificationService;
import com.haalkhata.feature.subscription.application.SubscriptionService;
import com.haalkhata.feature.subscription.domain.PaymentTransaction;
import com.haalkhata.feature.subscription.infrastructure.PaymentTransactionRepository;
import com.haalkhata.feature.subscription.infrastructure.SubscriptionRepository;
import com.haalkhata.feature.support.infrastructure.SupportTicketRepository;
import java.util.Arrays;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {
    private final AppProperties properties;
    private final ShopRepository shops;
    private final SubscriptionRepository subscriptions;
    private final PaymentTransactionRepository payments;
    private final SupportTicketRepository supportTickets;
    private final SubscriptionService subscriptionService;
    private final AppUserRepository users;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public Map<String, Object> overview(CurrentUser user) {
        requirePlatformAdmin(user);
        return Map.of(
                "shops", shops.count(),
                "activeSubscriptions", subscriptions.countByStatus("ACTIVE"),
                "pendingPayments", payments.countByStatus("PENDING"),
                "openSupportTickets", supportTickets.countByStatus("OPEN"),
                "waitingSupportTickets", supportTickets.countByStatus("WAITING_FOR_OWNER"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> paymentPage(CurrentUser user, int page, int size) {
        requirePlatformAdmin(user);
        int pageSize = Math.max(1, Math.min(size <= 0 ? 25 : size, 100));
        var result = payments.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), pageSize));
        return Map.of("items", result.getContent().stream().map(this::paymentJson).toList(),
                "page", result.getNumber(), "hasMore", result.hasNext(), "total", result.getTotalElements());
    }

    @Transactional
    public Map<String, Object> reconcile(CurrentUser user, String paymentId) {
        requirePlatformAdmin(user);
        return subscriptionService.reconcileAsPlatformAdmin(paymentId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> access(CurrentUser user) {
        return Map.of("platformAdmin", isPlatformAdmin(user));
    }

    @Transactional
    public Map<String, Object> sendPush(CurrentUser user, PushRequest request) {
        requirePlatformAdmin(user);
        if (request == null) throw BusinessException.badRequest("Notification details are required.");
        if (request.expiresAt() == null || !request.expiresAt().isAfter(Instant.now())) {
            throw BusinessException.badRequest("Choose a notification expiry time in the future.");
        }
        var audience = request.audience() == null ? "" : request.audience().trim().toUpperCase();
        var data = Map.of("screen", "notifications", "source", "platform_admin");
        if ("ALL".equals(audience)) {
            return notifications.sendPlatformBroadcast(user.getUserId(), request.title(), request.body(),
                    request.titleBn(), request.bodyBn(), request.imageUrl(), request.expiresAt(), data);
        }
        if (!"USER".equals(audience) || request.recipientEmail() == null || request.recipientEmail().isBlank()) {
            throw BusinessException.badRequest("Choose all users or enter a recipient email.");
        }
        var recipient = users.findByEmailIgnoreCase(request.recipientEmail().trim())
                .orElseThrow(() -> BusinessException.notFound("User account not found."));
        var result = new java.util.HashMap<>(notifications.sendPlatformToUser(user.getUserId(), recipient.getId(),
                request.title(), request.body(), request.titleBn(), request.bodyBn(), request.imageUrl(),
                request.expiresAt(), data));
        result.put("recipient", Map.of("userId", recipient.getId(), "name", recipient.getName(),
                "email", recipient.getEmail()));
        return result;
    }

    private Map<String, Object> paymentJson(PaymentTransaction payment) {
        return Map.ofEntries(
                Map.entry("paymentId", payment.getPaymentId()),
                Map.entry("shopId", payment.getShopId()),
                Map.entry("subscriptionId", payment.getSubscriptionId()),
                Map.entry("internalTransactionId", payment.getInternalTransactionId()),
                Map.entry("gatewayPaymentId", nullable(payment.getGatewayPaymentId())),
                Map.entry("gatewayTransactionId", nullable(payment.getGatewayTransactionId())),
                Map.entry("gateway", payment.getGateway()),
                Map.entry("amount", payment.getAmountBdt().toPlainString()),
                Map.entry("status", payment.getStatus()),
                Map.entry("errorCode", nullable(payment.getErrorCode())),
                Map.entry("errorMessage", nullable(payment.getErrorMessage())),
                Map.entry("createdAt", payment.getCreatedAt().toString()),
                Map.entry("updatedAt", payment.getUpdatedAt().toString()));
    }

    private String nullable(String value) { return value == null ? "" : value; }

    private boolean isPlatformAdmin(CurrentUser user) {
        Set<String> ids = Arrays.stream(properties.getPlatformAdminUserIds().split(","))
                .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toSet());
        return ids.contains(user.getUserId());
    }

    private void requirePlatformAdmin(CurrentUser user) {
        if (!isPlatformAdmin(user)) throw BusinessException.forbidden("Platform admin access required.");
    }

    public record PushRequest(String audience, String recipientEmail, String title, String body,
                              String titleBn, String bodyBn, String imageUrl, Instant expiresAt) {}
}
