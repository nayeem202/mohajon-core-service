package com.haalkhata.feature.notification.infrastructure;

import com.haalkhata.feature.notification.domain.FcmDeviceToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmDeviceTokenRepository extends JpaRepository<FcmDeviceToken, String> {

    Optional<FcmDeviceToken> findByToken(String token);

    List<FcmDeviceToken> findByShopIdAndActiveTrue(String shopId);

    List<FcmDeviceToken> findByShopIdAndActiveTrueAndNotificationTypesContaining(
            String shopId, String notificationType);

    List<FcmDeviceToken> findByUserIdAndActiveTrueAndNotificationTypesContaining(
            String userId, String notificationType);

    List<FcmDeviceToken> findByActiveTrueAndNotificationTypesContaining(String notificationType);
}
