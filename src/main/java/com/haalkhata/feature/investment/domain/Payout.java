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
@Table(name = "payouts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payout {

    @Id
    @Column(name = "payout_id", length = 36)
    private String payoutId;

    @Column(name = "investment_id", nullable = false, length = 36)
    private String investmentId;

    @Column(name = "investor_id", nullable = false, length = 36)
    private String investorId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(nullable = false)
    private double amount;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
