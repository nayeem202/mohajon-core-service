package com.haalkhata.feature.sale.returning.domain;

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
@Table(name = "sale_return_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleReturnItem {
    @Id
    @Column(name = "return_item_id", length = 36)
    private String returnItemId;
    @Column(name = "return_id", nullable = false, length = 36)
    private String returnId;
    @Column(name = "sale_item_id", nullable = false, length = 36)
    private String saleItemId;
    @Column(name = "product_id", length = 36)
    private String productId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false)
    private double qty;
    @Column(name = "unit_price", nullable = false)
    private double unitPrice;
    @Column(name = "purchase_price", nullable = false)
    private double purchasePrice;
}
