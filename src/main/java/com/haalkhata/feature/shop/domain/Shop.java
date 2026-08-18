package com.haalkhata.feature.shop.domain;

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
@Table(name = "shops")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shop {

    @Id
    @Column(name = "shop_id", length = 60)
    private String shopId;

    @Column(name = "owner_user_id", nullable = false, length = 36)
    private String ownerUserId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String location;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "owner_count", nullable = false)
    private int ownerCount;

    @Column(name = "total_investment", nullable = false)
    private double totalInvestment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
