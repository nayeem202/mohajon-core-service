package com.haalkhata.feature.expense.infrastructure;

import com.haalkhata.feature.expense.domain.Expense;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, String> {

    List<Expense> findByShopIdAndIsDeletedFalseOrderByDateDesc(String shopId);
    List<Expense> findByShopIdAndBranchIdAndIsDeletedFalseOrderByDateDesc(String shopId, String branchId);

    Optional<Expense> findByExpenseIdAndShopIdAndIsDeletedFalse(String expenseId, String shopId);
    Optional<Expense> findByExpenseIdAndShopIdAndBranchIdAndIsDeletedFalse(String expenseId, String shopId, String branchId);

    List<Expense> findByShopIdAndIsDeletedFalseAndDateBetweenOrderByDateDesc(
            String shopId, LocalDateTime from, LocalDateTime to);
    List<Expense> findByShopIdAndBranchIdAndIsDeletedFalseAndDateBetweenOrderByDateDesc(
            String shopId, String branchId, LocalDateTime from, LocalDateTime to);

    List<Expense> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<Expense> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);
    List<Expense> findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, String branchId, LocalDateTime after);
    List<Expense> findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, String branchId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
