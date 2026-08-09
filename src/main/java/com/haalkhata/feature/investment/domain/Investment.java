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
@Table(name = "investments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investment {

    @Id
    @Column(name = "investment_id", length = 36)
    private String investmentId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(name = "category_name", length = 150)
    private String categoryName;

    @Column(name = "total_investment", nullable = false)
    private double totalInvestment;

    @Column(name = "profit_share_method", nullable = false, length = 20)
    private String profitShareMethod;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false)
    private double roi;

    @Column(name = "total_profit", nullable = false)
    private double totalProfit;

    @Column(name = "total_paid", nullable = false)
    private double totalPaid;

    @Column(nullable = false)
    private double outstanding;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
