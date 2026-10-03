package com.haalkhata.feature.purchase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchase_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseItem {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "purchase_id", nullable = false, length = 36)
    private String purchaseId;

    @Column(name = "product_id", length = 36)
    private String productId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private double qty;

    @Column(name = "unit_cost", nullable = false)
    private double unitCost;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiry_date")
    private java.time.LocalDate expiryDate;
}
