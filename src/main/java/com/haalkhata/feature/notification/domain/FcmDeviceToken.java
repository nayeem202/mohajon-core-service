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
@Table(name = "fcm_device_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmDeviceToken {

    @Id
    @Column(name = "token_id", length = 36)
    private String tokenId;

    @Column(name = "user_id", nullable = false, length = 60)
    private String userId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String token;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "notification_types", nullable = false, columnDefinition = "text")
    private String notificationTypes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
