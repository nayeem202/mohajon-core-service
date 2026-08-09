package com.haalkhata.feature.sale.domain;

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
@Table(name = "sale_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleItem {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "sale_id", nullable = false, length = 36)
    private String saleId;

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
