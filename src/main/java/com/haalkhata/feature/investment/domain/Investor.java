package com.haalkhata.feature.investment.domain;

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
@Table(name = "investors")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investor {

    @Id
    @Column(name = "investor_id", length = 36)
    private String investorId;

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

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "total_investment", nullable = false)
    private double totalInvestment;

    @Column(name = "total_profit", nullable = false)
    private double totalProfit;

    @Column(name = "total_loss", nullable = false)
    private double totalLoss;

    @Column(nullable = false)
    private double outstanding;

    @Column(name = "investment_count", nullable = false)
    private int investmentCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
