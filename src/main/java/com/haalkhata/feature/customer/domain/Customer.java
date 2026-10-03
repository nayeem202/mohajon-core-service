package com.haalkhata.feature.customer.domain;

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
@Table(name = "customers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(columnDefinition = "text")
    private String address;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "total_spent", nullable = false)
    private double totalSpent;

    @Column(name = "credit_limit", nullable = false)
    private double creditLimit;

    @Column(name = "loyalty_points", nullable = false)
    private int loyaltyPoints;

    @Column(length = 50)
    private String tag;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
