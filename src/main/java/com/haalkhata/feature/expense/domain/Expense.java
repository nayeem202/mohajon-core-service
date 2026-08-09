package com.haalkhata.feature.expense.domain;

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
@Table(name = "expenses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @Column(name = "expense_id", length = 36)
    private String expenseId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false)
    private double amount;

    @Column(name = "category_id", length = 100)
    private String categoryId;

    @Column(name = "category_label", length = 150)
    private String categoryLabel;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
