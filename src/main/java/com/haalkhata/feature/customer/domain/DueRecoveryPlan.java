package com.haalkhata.feature.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "due_recovery_plan")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DueRecoveryPlan {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "promised_payment_date")
    private LocalDate promisedPaymentDate;

    @Column(name = "snoozed_until")
    private LocalDateTime snoozedUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
