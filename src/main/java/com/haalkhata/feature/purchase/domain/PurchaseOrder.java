package com.haalkhata.feature.purchase.domain;

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
@Table(name = "purchase_order")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;
    @Column(name = "branch_id", nullable = false, length = 80)
    private String branchId;

    @Column(name = "supplier_id", length = 36)
    private String supplierId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private double totalAmount;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "received_qty_json", columnDefinition = "text")
    private String receivedQtyJson;

    @Column(name = "delivered_qty_json", columnDefinition = "text")
    private String deliveredQtyJson;

    @Column(name = "receipts_json", columnDefinition = "text")
    private String receiptsJson;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;
}
