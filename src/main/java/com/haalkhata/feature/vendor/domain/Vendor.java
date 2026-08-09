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
@Table(name = "vendors")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vendor {

    @Id
    @Column(name = "vendor_id", length = 36)
    private String vendorId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "text")
    private String address;

    @Column(columnDefinition = "text")
    private String products;

    @Column
    private double rating;

    @Column(name = "total_orders", nullable = false)
    private int totalOrders;

    @Column(name = "total_spent", nullable = false)
    private double totalSpent;

    @Column(name = "pending_amount", nullable = false)
    private double pendingAmount;

    @Column(name = "last_purchase_date")
    private LocalDateTime lastPurchaseDate;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
