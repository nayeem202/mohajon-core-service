package com.haalkhata.feature.sale.returning.domain;

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
@Table(name = "sale_returns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReturn {
    @Id
    @Column(name = "return_id", length = 36)
    private String returnId;
    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;
    @Column(name = "branch_id", nullable = false, length = 80)
    private String branchId;
    @Column(name = "sale_id", nullable = false, length = 36)
    private String saleId;
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;
    @Column(name = "refund_amount", nullable = false)
    private double refundAmount;
    @Column(name = "refund_method", nullable = false, length = 30)
    private String refundMethod;
    @Column(length = 500)
    private String reason;
    @Column(name = "staff_id", length = 36)
    private String staffId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;
}
