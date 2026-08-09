package com.haalkhata.feature.expense.infrastructure;

import com.haalkhata.feature.expense.domain.Expense;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, String> {

    List<Expense> findByShopIdAndIsDeletedFalseOrderByDateDesc(String shopId);

    Optional<Expense> findByExpenseIdAndShopIdAndIsDeletedFalse(String expenseId, String shopId);

    List<Expense> findByShopIdAndIsDeletedFalseAndDateBetweenOrderByDateDesc(
            String shopId, LocalDateTime from, LocalDateTime to);

    List<Expense> findByShopIdAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    List<Expense> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
