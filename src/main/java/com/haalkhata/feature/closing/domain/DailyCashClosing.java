package com.haalkhata.feature.closing.domain;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity @Table(name = "daily_cash_closing")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DailyCashClosing {
    @Id @Column(name = "closing_id", length = 36) private String closingId;
    @Column(name = "shop_id", nullable = false, length = 60) private String shopId;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;
    @Column(name = "opened_by", nullable = false, length = 36) private String openedBy;
    @Column(name = "closed_by", length = 36) private String closedBy;
    @Column(name = "opening_cash", nullable = false) private double openingCash;
    @Column(name = "cash_sales", nullable = false) private double cashSales;
    @Column(name = "digital_sales", nullable = false) private double digitalSales;
    @Column(nullable = false) private double expenses;
    @Column(name = "expected_cash", nullable = false) private double expectedCash;
    @Column(name = "actual_cash") private Double actualCash;
    private Double variance;
    @Column(nullable = false, length = 20) private String status;
    @Column(columnDefinition = "text") private String note;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
