package com.haalkhata.feature.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_history")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class NotificationHistory {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "sender_user_id", nullable = false, length = 60)
    private String senderUserId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_for_everyone", nullable = false)
    private boolean deletedForEveryone;
}
