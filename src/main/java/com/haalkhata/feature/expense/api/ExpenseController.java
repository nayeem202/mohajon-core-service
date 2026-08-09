package com.haalkhata.feature.expense.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.expense.application.ExpenseService;
import com.haalkhata.feature.expense.application.ExpenseService.UpsertRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null || to != null) {
            LocalDate start = from == null ? LocalDate.of(2000, 1, 1) : from;
            LocalDate end = to == null ? LocalDate.now() : to;
            return expenseService.listBetween(user, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        }
        return expenseService.list(user);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody UpsertRequest request) {
        return expenseService.upsert(user, request);
    }

    @PutMapping("/{expenseId}")
    public Map<String, Object> update(@AuthenticationPrincipal CurrentUser user,
                                      @PathVariable String expenseId,
                                      @RequestBody UpsertRequest request) {
        return expenseService.upsert(user, withId(request, expenseId));
    }

    @DeleteMapping("/{expenseId}")
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String expenseId) {
        expenseService.delete(user, expenseId);
    }

    private UpsertRequest withId(UpsertRequest request, String expenseId) {
        return new UpsertRequest(expenseId, request.description(), request.amount(),
                request.categoryId(), request.categoryLabel(), request.date(),
                request.notes(), request.isDeleted());
    }
}
