package com.haalkhata.feature.notification.infrastructure;

import com.haalkhata.feature.notification.domain.PlatformNotification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformNotificationRepository extends JpaRepository<PlatformNotification, String> {
    @Query("select n from PlatformNotification n where n.audience = 'ALL' or n.recipientUserId = :userId order by n.createdAt desc")
    List<PlatformNotification> findForUser(@Param("userId") String userId, Pageable pageable);
}
