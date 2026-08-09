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
@Table(name = "vendor_purchases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPurchase {

    @Id
    @Column(name = "purchase_id", length = 36)
    private String purchaseId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "vendor_id", nullable = false, length = 36)
    private String vendorId;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "items_supplied", columnDefinition = "text")
    private String itemsSupplied;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
