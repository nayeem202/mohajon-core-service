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

@Entity
@Table(name = "platform_notifications")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformNotification {
    @Id @Column(length = 36) private String id;
    @Column(name = "sender_user_id", nullable = false, length = 60) private String senderUserId;
    @Column(nullable = false, length = 12) private String audience;
    @Column(name = "recipient_user_id", length = 60) private String recipientUserId;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(name = "title_bn", length = 255) private String titleBn;
    @Column(name = "body_bn", columnDefinition = "text") private String bodyBn;
    @Column(name = "image_url", length = 500) private String imageUrl;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
