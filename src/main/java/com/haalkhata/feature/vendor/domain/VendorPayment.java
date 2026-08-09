package com.haalkhata.feature.vendor.domain;

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
@Table(name = "vendor_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPayment {

    @Id
    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "vendor_id", nullable = false, length = 36)
    private String vendorId;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(length = 30)
    private String method;

    @Column(length = 200)
    private String reference;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
