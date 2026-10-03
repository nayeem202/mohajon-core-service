package com.haalkhata.feature.payment.domain;

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
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "sale_id", length = 36)
    private String saleId;

    @Column(nullable = false)
    private double amount;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(length = 200)
    private String reference;

    @Column(name = "staff_id", length = 36)
    private String staffId;

    @Column(name = "branch_id", length = 80)
    private String branchId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;
}
