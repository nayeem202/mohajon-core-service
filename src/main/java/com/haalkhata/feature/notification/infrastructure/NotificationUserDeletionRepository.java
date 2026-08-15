package com.haalkhata.feature.notification.infrastructure;

import com.haalkhata.feature.notification.domain.NotificationUserDeletion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationUserDeletionRepository extends JpaRepository<NotificationUserDeletion, String> {
    List<NotificationUserDeletion> findByUserIdAndNotificationIdIn(String userId, Collection<String> ids);
    Optional<NotificationUserDeletion> findByUserIdAndNotificationId(String userId, String notificationId);
}
