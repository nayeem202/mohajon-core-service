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
@Table(name = "subscription")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @Column(name = "subscription_id", length = 36)
    private String subscriptionId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "plan_code", nullable = false, length = 30)
    private String planCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "amount_bdt", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountBdt;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "internal_transaction_id", nullable = false, length = 64)
    private String internalTransactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}