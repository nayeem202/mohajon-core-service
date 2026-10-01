package com.haalkhata.feature.expense.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.core.util.TimeUtil;
import com.haalkhata.feature.expense.domain.Expense;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.guardian.application.GuardianService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ExpenseRepository expenseRepository;
    private final GuardianService guardianService;

    public record UpsertRequest(String expenseId, String description, Double amount,
                                String categoryId, String categoryLabel, String date,
                                String notes, Boolean isDeleted) {
    }

    public List<Map<String, Object>> list(CurrentUser user) {
        return expenseRepository.findByShopIdAndIsDeletedFalseOrderByDateDesc(user.getShopId())
                .stream().map(this::toJson).toList();
    }

    public List<Map<String, Object>> listBetween(CurrentUser user, LocalDateTime from, LocalDateTime to) {
        return expenseRepository.findByShopIdAndIsDeletedFalseAndDateBetweenOrderByDateDesc(
                        user.getShopId(), from, to)
                .stream().map(this::toJson).toList();
    }

    @Transactional
    public Map<String, Object> upsert(CurrentUser user, UpsertRequest request) {
        Expense expense;
        boolean isNew = request.expenseId() == null || request.expenseId().isBlank();
        if (isNew) {
            expense = Expense.builder()
                    .expenseId(Ids.uuid())
                    .shopId(user.getShopId())
                    .amount(0)
                    .date(LocalDateTime.now())
                    .isDeleted(false)
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            expense = expenseRepository.findByExpenseIdAndShopIdAndIsDeletedFalse(request.expenseId(), user.getShopId())
                    .orElseThrow(() -> BusinessException.notFound("Expense not found"));
        }
        expense.setDescription(request.description() == null ? expense.getDescription() : request.description().trim());
        if (request.amount() != null) {
            expense.setAmount(request.amount());
        }
        expense.setCategoryId(request.categoryId());
        expense.setCategoryLabel(request.categoryLabel());
        if (request.date() != null && !request.date().isBlank()) {
            expense.setDate(TimeUtil.parse(request.date(), expense.getDate()));
        }
        expense.setNotes(request.notes());
        expense.setDeleted(request.isDeleted() != null && request.isDeleted());
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);
        log.info("EXPENSE_{} expenseId={} shopId={} amount={}",
                isNew ? "CREATED" : "UPDATED", expense.getExpenseId(), user.getShopId(), expense.getAmount());
        guardianService.record(user, isNew ? "EXPENSE_CREATED" : "EXPENSE_UPDATED", "EXPENSE", expense.getExpenseId(), expense.getAmount(),
                Map.of("category", expense.getCategoryLabel() == null ? "" : expense.getCategoryLabel()));
        return toJson(expense);
    }

    @Transactional
    public void delete(CurrentUser user, String expenseId) {
        Expense expense = expenseRepository.findByExpenseIdAndShopIdAndIsDeletedFalse(expenseId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Expense not found"));
        expense.setDeleted(true);
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);
        log.info("EXPENSE_DELETED expenseId={} shopId={}", expenseId, user.getShopId());
        guardianService.record(user, "DELETE", "EXPENSE", expenseId, expense.getAmount(), Map.of("reason", "manual_delete"));
    }

    public Map<String, Object> toJson(Expense e) {
        Map<String, Object> json = new HashMap<>();
        json.put("expenseId", e.getExpenseId());
        json.put("description", e.getDescription());
        json.put("amount", e.getAmount());
        json.put("categoryId", e.getCategoryId());
        json.put("categoryLabel", e.getCategoryLabel());
        json.put("date", e.getDate().format(ISO));
        json.put("notes", e.getNotes());
        json.put("shopId", e.getShopId());
        json.put("updatedAt", e.getUpdatedAt().format(ISO));
        json.put("isDeleted", e.isDeleted());
        return json;
    }
}
