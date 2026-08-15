package com.haalkhata.feature.notification.application;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.notification.domain.FcmDeviceToken;
import com.haalkhata.feature.notification.domain.NotificationHistory;
import com.haalkhata.feature.notification.domain.NotificationUserDeletion;
import com.haalkhata.feature.notification.infrastructure.FcmDeviceTokenRepository;
import com.haalkhata.feature.notification.infrastructure.NotificationHistoryRepository;
import com.haalkhata.feature.notification.infrastructure.NotificationUserDeletionRepository;
import com.haalkhata.feature.auth.infrastructure.AppUserRepository;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final FcmDeviceTokenRepository tokenRepository;
    private final NotificationHistoryRepository historyRepository;
    private final NotificationUserDeletionRepository userDeletionRepository;
    private final AppUserRepository appUserRepository;

    @Value("${app.firebase-service-account-json:}")
    private String serviceAccountJson;

    @Value("${app.firebase-service-account-file:}")
    private String serviceAccountFile;

    @Transactional
    public FcmDeviceToken registerDevice(CurrentUser user, String token, String platform,
                                         Object enabledTypes) {
        if (token == null || token.isBlank()) {
            throw BusinessException.badRequest("FCM token is required.");
        }
        var now = LocalDateTime.now();
        var device = tokenRepository.findByToken(token).orElseGet(() -> FcmDeviceToken.builder()
                .tokenId(UUID.randomUUID().toString())
                .token(token)
                .createdAt(now)
                .build());
        device.setUserId(user.getUserId());
        device.setShopId(user.getShopId());
        device.setPlatform(platform == null || platform.isBlank() ? "unknown" : platform);
        device.setActive(true);
        device.setNotificationTypes(normalizeTypes(enabledTypes));
        device.setUpdatedAt(now);
        return tokenRepository.save(device);
    }

    @Transactional
    public void updatePreferences(CurrentUser user, String token, Object enabledTypes) {
        if (token == null || token.isBlank()) return;
        tokenRepository.findByToken(token).ifPresent(device -> {
            if (device.getShopId().equals(user.getShopId())) {
                device.setNotificationTypes(normalizeTypes(enabledTypes));
                device.setUpdatedAt(LocalDateTime.now());
                tokenRepository.save(device);
            }
        });
    }

    @Transactional
    public void disableDevice(CurrentUser user, String token) {
        tokenRepository.findByToken(token).ifPresent(device -> {
            if (device.getShopId().equals(user.getShopId())) {
                device.setActive(false);
                device.setUpdatedAt(LocalDateTime.now());
                tokenRepository.save(device);
            }
        });
    }

    @Transactional
    public Map<String, Object> send(CurrentUser user, String title, String body,
                                    Map<String, String> data) {
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw BusinessException.badRequest("Notification title and body are required.");
        }
        var type = data == null ? null : data.get("type");
        if ("system".equals(type)) type = "platform";
        var pushData = data == null ? new HashMap<String, String>() : new HashMap<>(data);
        if (type != null) pushData.put("type", type);
        var history = historyRepository.save(NotificationHistory.builder()
                .id(UUID.randomUUID().toString())
                .shopId(user.getShopId())
                .senderUserId(user.getUserId())
                .type(type == null || type.isBlank() ? "platform" : type)
                .title(title)
                .body(body)
                .createdAt(LocalDateTime.now())
                .build());
        pushData.put("notificationId", history.getId());
        pushData.put("senderUserId", user.getUserId());
        var devices = type == null || type.isBlank()
                ? tokenRepository.findByShopIdAndActiveTrue(user.getShopId())
                : tokenRepository.findByShopIdAndActiveTrueAndNotificationTypesContaining(
                        user.getShopId(), type);
        if (devices.isEmpty()) {
            log.warn("FCM notification has no active devices for shop {} and type {}",
                    user.getShopId(), type);
            return Map.of("sent", 0, "failed", 0, "registeredDevices", 0,
                    "notificationId", history.getId());
        }
        var app = firebaseApp();
        var tokens = devices.stream().map(FcmDeviceToken::getToken).toList();
        var message = MulticastMessage.builder()
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(pushData)
                .addAllTokens(tokens)
                .build();
        try {
            // sendMulticast uses the retired legacy /batch endpoint. The
            // per-message API uses current FCM HTTP v1 transport.
            BatchResponse response = FirebaseMessaging.getInstance(app)
                    .sendEachForMulticast(message);
            log.info("FCM notification delivered for shop {}: sent={}, failed={}, devices={}, type={}",
                    user.getShopId(), response.getSuccessCount(), response.getFailureCount(),
                    tokens.size(), type);
            return Map.of("sent", response.getSuccessCount(), "failed", response.getFailureCount(),
                    "registeredDevices", tokens.size(), "notificationId", history.getId());
        } catch (Exception exception) {
            // Do not leave a notification in shared history when FCM delivery
            // itself failed. Otherwise the sender sees an error, but the same
            // notification appears after the next login/history reload.
            historyRepository.deleteById(history.getId());
            log.error("FCM send failed for shop {} and {} device(s): {}", user.getShopId(),
                    tokens.size(), exception.getMessage(), exception);
            throw new BusinessException("FCM_SEND_FAILED", "Could not send notification.");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(CurrentUser user) {
        var history = historyRepository.findTop100ByShopIdOrderByCreatedAtDesc(user.getShopId())
                .stream().filter(item -> !item.isDeletedForEveryone()).toList();
        var ids = history.stream().map(NotificationHistory::getId).toList();
        var deleted = userDeletionRepository.findByUserIdAndNotificationIdIn(user.getUserId(), ids)
                .stream().map(NotificationUserDeletion::getNotificationId).collect(Collectors.toSet());
        return history.stream().filter(item -> !deleted.contains(item.getId()))
                .map(item -> Map.<String, Object>of(
                        "id", item.getId(),
                        "shopId", item.getShopId(),
                        "senderUserId", item.getSenderUserId(),
                        "type", item.getType(),
                        "title", item.getTitle(),
                        "description", item.getBody(),
                        "timestamp", item.getCreatedAt().toString(),
                        "read", false,
                        "category", ("platform".equals(item.getType()) || "system".equals(item.getType()))
                                ? "platform" : "shop",
                        "sourceKey", "server:" + item.getId()))
                .toList();
    }

    @Transactional
    public void deleteForUser(CurrentUser user, String notificationId) {
        var item = historyRepository.findById(notificationId)
                .filter(value -> value.getShopId().equals(user.getShopId()))
                .orElseThrow(() -> BusinessException.notFound("Notification not found."));
        userDeletionRepository.save(NotificationUserDeletion.builder()
                .id(UUID.randomUUID().toString())
                .notificationId(item.getId())
                .userId(user.getUserId())
                .deletedAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void deleteForEveryone(CurrentUser user, String notificationId) {
        var item = historyRepository.findById(notificationId)
                .filter(value -> value.getShopId().equals(user.getShopId()))
                .orElseThrow(() -> BusinessException.notFound("Notification not found."));
        var role = appUserRepository.findById(user.getUserId()).map(value -> value.getRole())
                .orElse(user.getRole());
        var owner = role != null && (role.equalsIgnoreCase("OWNER") || role.equalsIgnoreCase("ADMIN"));
        if (!owner && !item.getSenderUserId().equals(user.getUserId())) {
            throw BusinessException.forbidden("Only the sender or shop owner can delete for everyone.");
        }
        item.setDeletedForEveryone(true);
        historyRepository.save(item);
    }

    private String normalizeTypes(Object value) {
        if (!(value instanceof Collection<?> values) || values.isEmpty()) {
            return "sale,stock,expense,payment,investment,platform";
        }
        return values.stream().map(Object::toString)
                .filter(type -> type.matches("sale|stock|expense|payment|investment|platform"))
                .distinct().sorted().collect(Collectors.joining(","));
    }

    private FirebaseApp firebaseApp() {
        if ((serviceAccountJson == null || serviceAccountJson.isBlank())
                && (serviceAccountFile == null || serviceAccountFile.isBlank())) {
            throw new BusinessException("FCM_NOT_CONFIGURED",
                    "Firebase service account is not configured on the server.");
        }
        if (!FirebaseApp.getApps().isEmpty()) return FirebaseApp.getInstance();
        try {
            var credentials = serviceAccountFile != null && !serviceAccountFile.isBlank()
                    ? GoogleCredentials.fromStream(new FileInputStream(serviceAccountFile))
                    : GoogleCredentials.fromStream(new ByteArrayInputStream(
                            serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
            return FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException("FCM_CONFIG_INVALID",
                    "Firebase service account configuration is invalid.");
        }
    }
}
