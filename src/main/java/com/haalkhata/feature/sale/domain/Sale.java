package com.haalkhata.feature.sale.domain;

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
@Table(name = "sales")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sale {

    @Id
    @Column(name = "sale_id", length = 36)
    private String saleId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(nullable = false)
    private double discount;

    @Column(name = "vat_percent", nullable = false)
    private double vatPercent;

    @Column(nullable = false)
    private double charge;

    @Column(name = "total_amount", nullable = false)
    private double totalAmount;

    @Column(name = "paid_amount", nullable = false)
    private double paidAmount;

    @Column(name = "due_amount", nullable = false)
    private double dueAmount;

    @Column(name = "payment_status", nullable = false, length = 10)
    private String paymentStatus;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "staff_id", length = 36)
    private String staffId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;
}
