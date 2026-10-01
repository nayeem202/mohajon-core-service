package com.haalkhata.feature.purchase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchase_order_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItem {

    @Id
    @Column(name = "item_id", length = 36)
    private String itemId;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private double qty;

    @Column(name = "unit_cost", nullable = false)
    private double unitCost;
}
