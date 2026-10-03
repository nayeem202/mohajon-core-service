package com.haalkhata.feature.purchase.returning.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
@Entity @Table(name="supplier_returns") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SupplierReturn {
    @Id @Column(name="return_id", length=36) private String returnId;
    @Column(name="shop_id", nullable=false, length=60) private String shopId;
    @Column(name="branch_id", nullable=false, length=80) private String branchId;
    @Column(name="order_id", nullable=false, length=36) private String orderId;
    @Column(name="supplier_id", length=36) private String supplierId;
    @Column(name="items_json", nullable=false, columnDefinition="text") private String itemsJson;
    @Column(name="total_amount", nullable=false) private double totalAmount;
    @Column(nullable=false, length=500) private String reason;
    @Column(name="actor_id", length=36) private String actorId;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @Column(name="is_deleted", nullable=false) private boolean isDeleted;
}
