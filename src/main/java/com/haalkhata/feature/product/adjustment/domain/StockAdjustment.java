package com.haalkhata.feature.product.adjustment.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
@Entity @Table(name="stock_adjustments") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockAdjustment {
    @Id @Column(name="adjustment_id", length=36) private String adjustmentId;
    @Column(name="shop_id", nullable=false, length=60) private String shopId;
    @Column(name="branch_id", nullable=false, length=80) private String branchId;
    @Column(name="product_id", nullable=false, length=36) private String productId;
    @Column(nullable=false) private int delta;
    @Column(nullable=false, length=500) private String reason;
    @Column(name="actor_id", nullable=false, length=36) private String actorId;
    @Column(name="approver_id", length=36) private String approverId;
    @Column(nullable=false, length=20) private String status;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @Column(name="is_deleted", nullable=false) private boolean isDeleted;
}
