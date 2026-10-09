package com.haalkhata.feature.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscription_plan")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    @Id
    @Column(name = "plan_code", length = 30)
    private String planCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_bdt", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceBdt;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "trial_days")
    private Integer trialDays;

    /** FREE, PRO, BUSINESS, or TRIAL. Kept in the database so plans can be
     * changed without a mobile-app release. */
    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "ai_credits_per_month", nullable = false)
    private int aiCreditsPerMonth;

    @Column(name = "ai_welcome_credits", nullable = false)
    private int aiWelcomeCredits;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
