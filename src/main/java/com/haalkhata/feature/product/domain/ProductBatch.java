package com.haalkhata.feature.product.domain;

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
@Table(name = "product_batches")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductBatch {
    @Id @Column(name = "batch_id", length = 36) private String batchId;
    @Column(name = "shop_id", nullable = false, length = 60) private String shopId;
    @Column(name = "branch_id", nullable = false, length = 80) private String branchId;
    @Column(name = "product_id", nullable = false, length = 36) private String productId;
    @Column(name = "batch_number", nullable = false, length = 100) private String batchNumber;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_cost", nullable = false) private double unitCost;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "is_deleted", nullable = false) private boolean isDeleted;
}
