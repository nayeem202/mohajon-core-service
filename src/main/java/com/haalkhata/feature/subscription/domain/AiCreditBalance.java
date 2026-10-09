package com.haalkhata.feature.subscription.domain;

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
@Table(name = "ai_credit_balance")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCreditBalance {
    @Id
    @Column(name = "shop_id", length = 60)
    private String shopId;
    @Column(name = "welcome_credits_remaining", nullable = false)
    private int welcomeCreditsRemaining;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "monthly_credits", nullable = false)
    private int monthlyCredits;
    @Column(name = "monthly_credits_used", nullable = false)
    private int monthlyCreditsUsed;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
