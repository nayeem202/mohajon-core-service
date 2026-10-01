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
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @Column(name = "product_id", length = 36)
    private String productId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @Column(name = "name_bn", length = 200)
    private String nameBn;

    @Column(length = 100)
    private String barcode;

    @Column(length = 100)
    private String category;

    @Column(length = 30)
    private String unit;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "buying_price", nullable = false)
    private double buyingPrice;

    @Column(name = "purchase_price", nullable = false)
    private double purchasePrice;

    @Column(name = "selling_price", nullable = false)
    private double sellingPrice;

    @Column(name = "stock_qty", nullable = false)
    private int stockQty;

    @Column(name = "low_stock_alert", nullable = false)
    private int lowStockAlert;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
