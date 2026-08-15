package com.haalkhata.feature.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;

@Entity
@Table(name = "notification_user_deletions")
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationUserDeletion {
    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "notification_id", nullable = false, length = 36)
    private String notificationId;
    @Column(name = "user_id", nullable = false, length = 60)
    private String userId;
    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;
}
