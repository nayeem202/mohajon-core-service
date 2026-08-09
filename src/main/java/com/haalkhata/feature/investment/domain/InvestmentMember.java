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
@Table(name = "investment_members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentMember {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "investment_id", nullable = false, length = 36)
    private String investmentId;

    @Column(name = "investor_id", nullable = false, length = 36)
    private String investorId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "investment_amount", nullable = false)
    private double investmentAmount;

    @Column(name = "share_percentage", nullable = false)
    private double sharePercentage;

    @Column(nullable = false)
    private double profit;

    @Column(nullable = false)
    private double loss;

    @Column(name = "paid_amount", nullable = false)
    private double paidAmount;

    @Column(name = "remaining_amount", nullable = false)
    private double remainingAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
