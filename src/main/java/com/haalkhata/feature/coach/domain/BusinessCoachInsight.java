package com.haalkhata.feature.coach.domain;

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
@Table(name = "business_coach_insight")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BusinessCoachInsight {
    @Id @Column(name = "insight_id", length = 36)
    private String insightId;
    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;
    @Column(name = "branch_id", nullable = false, length = 80)
    private String branchId;
    @Column(name = "insight_date", nullable = false)
    private LocalDate insightDate;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(nullable = false)
    private int priority;
    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;
    @Column(name = "title_bn", nullable = false, length = 255)
    private String titleBn;
    @Column(name = "body_en", nullable = false, columnDefinition = "text")
    private String bodyEn;
    @Column(name = "body_bn", nullable = false, columnDefinition = "text")
    private String bodyBn;
    @Column(name = "action_en", nullable = false, length = 255)
    private String actionEn;
    @Column(name = "action_bn", nullable = false, length = 255)
    private String actionBn;
    private Double amount;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
