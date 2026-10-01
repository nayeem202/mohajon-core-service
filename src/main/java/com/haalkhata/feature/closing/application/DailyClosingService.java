package com.haalkhata.feature.closing.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.closing.domain.DailyCashClosing;
import com.haalkhata.feature.closing.infrastructure.DailyCashClosingRepository;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.guardian.application.GuardianService;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class DailyClosingService {
    private final DailyCashClosingRepository repository;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final GuardianService guardian;

    @Transactional(readOnly = true)
    public Map<String, Object> current(CurrentUser user, LocalDate date) {
        DailyCashClosing closing = repository.findByShopIdAndBusinessDate(user.getShopId(), date).orElse(null);
        return closing == null ? calculation(user, date) : toMap(closing);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(CurrentUser user) {
        return repository.findTop30ByShopIdOrderByBusinessDateDesc(user.getShopId()).stream().map(this::toMap).toList();
    }

    @Transactional
    public Map<String, Object> close(CurrentUser user, double openingCash, double actualCash, String note) {
        LocalDate date = LocalDate.now();
        Map<String, Object> calculated = calculation(user, date);
        double expected = openingCash + number(calculated, "cashSales") - number(calculated, "expenses");
        DailyCashClosing closing = repository.findByShopIdAndBusinessDate(user.getShopId(), date).orElseGet(() -> DailyCashClosing.builder()
                .closingId(Ids.uuid()).shopId(user.getShopId()).businessDate(date).openedBy(user.getUserId()).createdAt(LocalDateTime.now()).build());
        if ("CLOSED".equals(closing.getStatus())) throw BusinessException.conflict("This business day is already closed");
        closing.setOpeningCash(openingCash); closing.setCashSales(number(calculated, "cashSales")); closing.setDigitalSales(number(calculated, "digitalSales"));
        closing.setExpenses(number(calculated, "expenses")); closing.setExpectedCash(expected); closing.setActualCash(actualCash); closing.setVariance(actualCash - expected);
        closing.setClosedBy(user.getUserId()); closing.setStatus("CLOSED"); closing.setNote(note); closing.setUpdatedAt(LocalDateTime.now());
        repository.save(closing);
        guardian.record(user, "DAY_CLOSED", "CASH_CLOSING", closing.getClosingId(), closing.getVariance(), Map.of("date", date.toString(), "expected", expected, "actual", actualCash));
        return toMap(closing);
    }

    private Map<String, Object> calculation(CurrentUser user, LocalDate date) {
        LocalDateTime from = date.atStartOfDay(), to = date.plusDays(1).atStartOfDay();
        var dayPayments = payments.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId()).stream().filter(p -> !p.getCreatedAt().isBefore(from) && p.getCreatedAt().isBefore(to)).toList();
        var dayExpenses = expenses.findByShopIdAndIsDeletedFalseOrderByDateDesc(user.getShopId()).stream().filter(e -> !e.getDate().isBefore(from) && e.getDate().isBefore(to)).toList();
        double cash = dayPayments.stream().filter(p -> "CASH".equalsIgnoreCase(p.getPaymentMethod())).mapToDouble(p -> p.getAmount()).sum();
        double digital = dayPayments.stream().filter(p -> !"CASH".equalsIgnoreCase(p.getPaymentMethod())).mapToDouble(p -> p.getAmount()).sum();
        double out = dayExpenses.stream().mapToDouble(e -> e.getAmount()).sum();
        Map<String, Object> m = new LinkedHashMap<>(); m.put("businessDate", date); m.put("cashSales", cash); m.put("digitalSales", digital); m.put("expenses", out); m.put("status", "OPEN"); return m;
    }
    private double number(Map<String,Object> m,String key){return ((Number)m.getOrDefault(key,0)).doubleValue();}
    private Map<String,Object> toMap(DailyCashClosing c){Map<String,Object> m=new LinkedHashMap<>(); m.put("closingId",c.getClosingId()); m.put("businessDate",c.getBusinessDate()); m.put("openingCash",c.getOpeningCash()); m.put("cashSales",c.getCashSales()); m.put("digitalSales",c.getDigitalSales()); m.put("expenses",c.getExpenses()); m.put("expectedCash",c.getExpectedCash()); m.put("actualCash",c.getActualCash()); m.put("variance",c.getVariance()); m.put("status",c.getStatus()); m.put("note",c.getNote()); m.put("updatedAt",c.getUpdatedAt()); return m;}
}
