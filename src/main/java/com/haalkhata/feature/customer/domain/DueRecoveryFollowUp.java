package com.haalkhata.feature.customer.domain;

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
@Table(name = "due_recovery_follow_up")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DueRecoveryFollowUp {

    @Id
    @Column(name = "follow_up_id", length = 36)
    private String followUpId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
