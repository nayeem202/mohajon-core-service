package com.haalkhata.feature.subscription.domain;

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
@Table(name = "ai_credit_usage")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCreditUsage {
    @Id
    @Column(name = "usage_id", length = 36)
    private String usageId;
    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;
    @Column(nullable = false, length = 40)
    private String feature;
    @Column(name = "monthly_credits_used", nullable = false)
    private int monthlyCreditsUsed;
    @Column(name = "welcome_credits_used", nullable = false)
    private int welcomeCreditsUsed;
    @Column(nullable = false)
    private boolean refunded;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
}
