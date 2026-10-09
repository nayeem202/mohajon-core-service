package com.haalkhata.feature.notification.infrastructure;

import com.haalkhata.feature.notification.domain.PlatformNotificationUserDeletion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformNotificationUserDeletionRepository
        extends JpaRepository<PlatformNotificationUserDeletion, String> {
    List<PlatformNotificationUserDeletion> findByUserIdAndNotificationIdIn(
            String userId, Collection<String> notificationIds);
}
