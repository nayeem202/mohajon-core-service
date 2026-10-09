package com.haalkhata.feature.notification.application;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.notification.domain.FcmDeviceToken;
import com.haalkhata.feature.notification.domain.NotificationHistory;
import com.haalkhata.feature.notification.domain.NotificationUserDeletion;
import com.haalkhata.feature.notification.domain.PlatformNotification;
import com.haalkhata.feature.notification.domain.PlatformNotificationUserDeletion;
import com.haalkhata.feature.notification.infrastructure.FcmDeviceTokenRepository;
import com.haalkhata.feature.notification.infrastructure.NotificationHistoryRepository;
import com.haalkhata.feature.notification.infrastructure.NotificationUserDeletionRepository;
import com.haalkhata.feature.notification.infrastructure.PlatformNotificationRepository;
import com.haalkhata.feature.notification.infrastructure.PlatformNotificationUserDeletionRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private static final int FCM_MULTICAST_LIMIT = 500;

    private final FcmDeviceTokenRepository tokenRepository;
    private final NotificationHistoryRepository historyRepository;
    private final NotificationUserDeletionRepository userDeletionRepository;
    private final PlatformNotificationRepository platformNotificationRepository;
    private final PlatformNotificationUserDeletionRepository platformNotificationUserDeletionRepository;
    private final AppUserRepository appUserRepository;

    @Value("${app.firebase-service-account-json:}")
    private String serviceAccountJson;

    @Value("${app.firebase-service-account-file:}")
    private String serviceAccountFile;

    @Transactional
    public FcmDeviceToken registerDevice(CurrentUser user, String token, String platform,
                                         Object enabledTypes, String languageCode) {
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
        device.setLanguageCode(normalizeLanguageCode(languageCode));
        device.setUpdatedAt(now);
        return tokenRepository.save(device);
    }

    @Transactional
    public void updatePreferences(CurrentUser user, String token, Object enabledTypes,
                                  String languageCode) {
        if (token == null || token.isBlank()) return;
        tokenRepository.findByToken(token).ifPresent(device -> {
            if (device.getShopId().equals(user.getShopId())) {
                device.setNotificationTypes(normalizeTypes(enabledTypes));
                device.setLanguageCode(normalizeLanguageCode(languageCode));
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
        return sendLocalized(user, title, body, title, body, data);
    }

    /**
     * Sends a built-in notification in the language selected on each device.
     * History retains the English template so older app versions still have a
     * readable entry; current clients resolve known templates locally.
     */
    @Transactional
    public Map<String, Object> sendLocalized(CurrentUser user, String title, String body,
                                             String titleBn, String bodyBn,
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
        try {
            var englishDevices = devices.stream()
                    .filter(device -> !"bn".equals(device.getLanguageCode())).toList();
            var banglaDevices = devices.stream()
                    .filter(device -> "bn".equals(device.getLanguageCode())).toList();
            var englishResponse = sendToDevices(englishDevices, title, body, pushData);
            var banglaResponse = sendToDevices(banglaDevices,
                    titleBn == null || titleBn.isBlank() ? title : titleBn,
                    bodyBn == null || bodyBn.isBlank() ? body : bodyBn, pushData);
            var sent = englishResponse.sent() + banglaResponse.sent();
            var failed = englishResponse.failed() + banglaResponse.failed();
            log.info("FCM notification delivered for shop {}: sent={}, failed={}, devices={}, type={}",
                    user.getShopId(), sent, failed, devices.size(), type);
            return Map.of("sent", sent, "failed", failed,
                    "registeredDevices", devices.size(), "notificationId", history.getId());
        } catch (Exception exception) {
            // Do not leave a notification in shared history when FCM delivery
            // itself failed. Otherwise the sender sees an error, but the same
            // notification appears after the next login/history reload.
            historyRepository.deleteById(history.getId());
            log.error("FCM send failed for shop {} and {} device(s): {}", user.getShopId(),
                    devices.size(), exception.getMessage(), exception);
            throw new BusinessException("FCM_SEND_FAILED", "Could not send notification.");
        }
    }

    /** Sends an account-specific push without exposing it in shop-wide history. */
    @Transactional
    public void sendToUser(String userId, String title, String body,
                           String titleBn, String bodyBn, Map<String, String> data) {
        if (userId == null || userId.isBlank()) return;
        var pushData = data == null ? new HashMap<String, String>() : new HashMap<>(data);
        pushData.put("type", "platform");
        var devices = tokenRepository.findByUserIdAndActiveTrueAndNotificationTypesContaining(
                userId, "platform");
        if (devices.isEmpty()) return;
        try {
            var english = devices.stream()
                    .filter(device -> !"bn".equals(device.getLanguageCode())).toList();
            var bangla = devices.stream()
                    .filter(device -> "bn".equals(device.getLanguageCode())).toList();
            sendToDevices(english, title, body, pushData);
            sendToDevices(bangla,
                    titleBn == null || titleBn.isBlank() ? title : titleBn,
                    bodyBn == null || bodyBn.isBlank() ? body : bodyBn, pushData);
        } catch (Exception exception) {
            log.error("Account notification failed for user {}: {}", userId,
                    exception.getMessage(), exception);
            throw new BusinessException("FCM_SEND_FAILED", "Could not send notification.");
        }
    }

    /**
     * Platform-only broadcast. The caller authorization belongs to the
     * platform-admin service; this method deliberately only handles delivery.
     */
    @Transactional
    public Map<String, Object> sendPlatformBroadcast(String senderUserId, String title, String body,
                                                       String titleBn, String bodyBn, String imageUrl,
                                                       Map<String, String> data) {
        return sendPlatformToDevices(senderUserId, "ALL", null,
                tokenRepository.findByActiveTrueAndNotificationTypesContaining("platform"),
                title, body, titleBn, bodyBn, imageUrl, data);
    }

    @Transactional
    public Map<String, Object> sendPlatformToUser(String senderUserId, String userId, String title, String body,
                                                    String titleBn, String bodyBn, String imageUrl,
                                                    Map<String, String> data) {
        return sendPlatformToDevices(senderUserId, "USER", userId,
                tokenRepository.findByUserIdAndActiveTrueAndNotificationTypesContaining(userId, "platform"),
                title, body, titleBn, bodyBn, imageUrl, data);
    }

    private Map<String, Object> sendPlatformToDevices(String senderUserId, String audience,
                                                        String recipientUserId,
                                                        List<FcmDeviceToken> devices,
                                                        String title, String body,
                                                        String titleBn, String bodyBn, String imageUrl,
                                                        Map<String, String> data) {
        validatePlatformMessage(title, body, titleBn, bodyBn, imageUrl);
        var inboxItem = platformNotificationRepository.save(PlatformNotification.builder()
                .id(UUID.randomUUID().toString())
                .senderUserId(senderUserId)
                .audience(audience)
                .recipientUserId(recipientUserId)
                .title(title.trim())
                .body(body == null ? "" : body.trim())
                .titleBn(blankToNull(titleBn))
                .bodyBn(blankToNull(bodyBn))
                .imageUrl(blankToNull(imageUrl))
                .createdAt(LocalDateTime.now())
                .build());
        var pushData = data == null ? new HashMap<String, String>() : new HashMap<>(data);
        pushData.put("type", "platform");
        pushData.put("platformInbox", "true");
        pushData.put("notificationId", inboxItem.getId());
        pushData.put("senderUserId", senderUserId);
        var english = devices.stream().filter(device -> !"bn".equals(device.getLanguageCode())).toList();
        var bangla = devices.stream().filter(device -> "bn".equals(device.getLanguageCode())).toList();
        try {
            var en = sendToDevices(english, title, body == null ? "" : body, pushData);
            var bn = sendToDevices(bangla,
                    titleBn == null || titleBn.isBlank() ? title : titleBn,
                    bodyBn == null || bodyBn.isBlank() ? (body == null ? "" : body) : bodyBn, pushData);
            return Map.of("sent", en.sent() + bn.sent(), "failed", en.failed() + bn.failed(),
                    "registeredDevices", devices.size(), "notificationId", inboxItem.getId(),
                    "inAppStored", true);
        } catch (Exception exception) {
            log.error("Platform push failed for {} device(s): {}", devices.size(), exception.getMessage(), exception);
            // The durable inbox message is already saved, so a temporary FCM issue must not
            // turn a successful in-app announcement into a loss of communication.
            return Map.of("sent", 0, "failed", devices.size(), "registeredDevices", devices.size(),
                    "notificationId", inboxItem.getId(), "inAppStored", true,
                    "deliveryError", true);
        }
    }

    private void validatePlatformMessage(String title, String body, String titleBn, String bodyBn, String imageUrl) {
        if (title == null || title.isBlank() || ((body == null || body.isBlank())
                && (imageUrl == null || imageUrl.isBlank()))) {
            throw BusinessException.badRequest("Notification title and a message or image are required.");
        }
        if (title.length() > 120 || (body != null && body.length() > 1200)
                || (titleBn != null && titleBn.length() > 120)
                || (bodyBn != null && bodyBn.length() > 1200)
                || (imageUrl != null && !imageUrl.isBlank()
                        && (!imageUrl.startsWith("/api/v1/files/") || imageUrl.length() > 500))) {
            throw BusinessException.badRequest("Notification text is too long.");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(CurrentUser user) {
        var shopHistory = historyRepository.findTop100ByShopIdOrderByCreatedAtDesc(user.getShopId())
                .stream().filter(item -> !item.isDeletedForEveryone()).toList();
        var ids = shopHistory.stream().map(NotificationHistory::getId).toList();
        var deleted = userDeletionRepository.findByUserIdAndNotificationIdIn(user.getUserId(), ids)
                .stream().map(NotificationUserDeletion::getNotificationId).collect(Collectors.toSet());
        var shopItems = shopHistory.stream().filter(item -> !deleted.contains(item.getId()))
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
        var platformHistory = platformNotificationRepository.findForUser(user.getUserId(), PageRequest.of(0, 100));
        var platformIds = platformHistory.stream().map(PlatformNotification::getId).toList();
        var platformDeleted = platformNotificationUserDeletionRepository
                .findByUserIdAndNotificationIdIn(user.getUserId(), platformIds)
                .stream().map(PlatformNotificationUserDeletion::getNotificationId)
                .collect(Collectors.toSet());
        var platformItems = platformHistory.stream()
                .filter(item -> !platformDeleted.contains(item.getId()))
                .map(item -> platformHistoryJson(user, item))
                .toList();
        return java.util.stream.Stream.concat(shopItems.stream(), platformItems.stream())
                .sorted((left, right) -> right.get("timestamp").toString()
                        .compareTo(left.get("timestamp").toString()))
                .limit(100)
                .toList();
    }

    private Map<String, Object> platformHistoryJson(CurrentUser user, PlatformNotification item) {
        var result = new HashMap<String, Object>();
        result.put("id", item.getId());
        result.put("shopId", user.getShopId());
        result.put("senderUserId", item.getSenderUserId());
        result.put("type", "platform");
        result.put("title", item.getTitle());
        result.put("description", item.getBody());
        if (item.getTitleBn() != null) result.put("titleBn", item.getTitleBn());
        if (item.getBodyBn() != null) result.put("descriptionBn", item.getBodyBn());
        if (item.getImageUrl() != null) result.put("imageUrl", item.getImageUrl());
        result.put("timestamp", item.getCreatedAt().toString());
        result.put("read", false);
        result.put("category", "platform");
        result.put("sourceKey", "platform:" + item.getId());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> homeAnnouncements(CurrentUser user) {
        return platformNotificationRepository.findForUser(user.getUserId(), PageRequest.of(0, 3))
                .stream().map(item -> platformHistoryJson(user, item)).toList();
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
    public void deletePlatformForUser(CurrentUser user, String notificationId) {
        platformNotificationRepository.findById(notificationId)
                .filter(item -> "ALL".equals(item.getAudience())
                        || user.getUserId().equals(item.getRecipientUserId()))
                .orElseThrow(() -> BusinessException.notFound("Notification not found."));
        platformNotificationUserDeletionRepository.save(PlatformNotificationUserDeletion.builder()
                .id(UUID.randomUUID().toString())
                .notificationId(notificationId)
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

    private String normalizeLanguageCode(String value) {
        return "bn".equalsIgnoreCase(value == null ? "" : value.trim()) ? "bn" : "en";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PushDelivery sendToDevices(List<FcmDeviceToken> devices, String title, String body,
                                       Map<String, String> data) throws Exception {
        if (devices.isEmpty()) return new PushDelivery(0, 0);
        var sent = 0;
        var failed = 0;
        for (var start = 0; start < devices.size(); start += FCM_MULTICAST_LIMIT) {
            var batch = devices.subList(start, Math.min(start + FCM_MULTICAST_LIMIT, devices.size()));
            var message = MulticastMessage.builder()
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putAllData(data)
                    .addAllTokens(batch.stream().map(FcmDeviceToken::getToken).toList())
                    .build();
            // sendEachForMulticast uses Firebase's current HTTP v1 transport.
            var response = FirebaseMessaging.getInstance(firebaseApp()).sendEachForMulticast(message);
            sent += response.getSuccessCount();
            failed += response.getFailureCount();
        }
        return new PushDelivery(sent, failed);
    }

    private record PushDelivery(int sent, int failed) {}

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
