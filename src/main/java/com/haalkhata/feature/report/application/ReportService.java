package com.haalkhata.feature.report.application;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.customer.infrastructure.CustomerLedgerEntryRepository;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CustomerRepository customerRepository;
    private final CustomerLedgerEntryRepository ledgerRepository;
    private final ExpenseRepository expenseRepository;
    private final ProductRepository productRepository;

    public Map<String, Object> salesSummary(CurrentUser user, LocalDate from, LocalDate to) {
        LocalDateTime start = (from == null ? LocalDate.of(2000, 1, 1) : from).atStartOfDay();
        LocalDateTime end = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();
        List<Map<String, Object>> sales = saleRepository
                .findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId()).stream()
                .filter(s -> !s.getCreatedAt().isBefore(start) && s.getCreatedAt().isBefore(end))
                .map(s -> {
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("saleId", s.getSaleId());
                    json.put("customerId", s.getCustomerId());
                    json.put("discount", s.getDiscount());
                    json.put("vatPercent", s.getVatPercent());
                    json.put("charge", s.getCharge());
                    json.put("totalAmount", s.getTotalAmount());
                    json.put("paidAmount", s.getPaidAmount());
                    json.put("dueAmount", s.getDueAmount());
                    json.put("paymentStatus", s.getPaymentStatus());
                    json.put("createdAt", s.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    return json;
                }).toList();

        double total = 0;
        double paid = 0;
        double due = 0;
        for (var s : sales) {
            total += (Double) s.get("totalAmount");
            paid += (Double) s.get("paidAmount");
            due += (Double) s.get("dueAmount");
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("from", start.format(DateTimeFormatter.ISO_LOCAL_DATE));
        json.put("to", end.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
        json.put("saleCount", sales.size());
        json.put("totalSales", total);
        json.put("totalPaid", paid);
        json.put("totalDue", due);
        json.put("sales", sales);
        return json;
    }

    public List<Map<String, Object>> inventory(CurrentUser user) {
        return productRepository.findByShopIdAndIsDeletedFalseOrderByNameEnAsc(user.getShopId()).stream()
                .map(p -> {
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("productId", p.getProductId());
                    json.put("name", p.getNameEn());
                    json.put("barcode", p.getBarcode());
                    json.put("category", p.getCategory());
                    json.put("stockQty", p.getStockQty());
                    json.put("buyingPrice", p.getBuyingPrice());
                    json.put("purchasePrice", p.getPurchasePrice());
                    json.put("sellingPrice", p.getSellingPrice());
                    json.put("lowStockAlert", p.getLowStockAlert());
                    json.put("stockValue", p.getStockQty() * p.getPurchasePrice());
                    return json;
                }).toList();
    }

    public Map<String, Object> expenseReport(CurrentUser user, LocalDate from, LocalDate to) {
        LocalDateTime start = (from == null ? LocalDate.of(2000, 1, 1) : from).atStartOfDay();
        LocalDateTime end = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();
        List<Map<String, Object>> expenses = expenseRepository
                .findByShopIdAndIsDeletedFalseAndDateBetweenOrderByDateDesc(user.getShopId(), start, end)
                .stream().map(e -> {
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("expenseId", e.getExpenseId());
                    json.put("description", e.getDescription());
                    json.put("amount", e.getAmount());
                    json.put("categoryId", e.getCategoryId());
                    json.put("categoryLabel", e.getCategoryLabel());
                    json.put("date", e.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    return json;
                }).toList();
        double total = expenses.stream().mapToDouble(e -> (Double) e.get("amount")).sum();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("from", start.format(DateTimeFormatter.ISO_LOCAL_DATE));
        json.put("to", end.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
        json.put("expenseCount", expenses.size());
        json.put("totalExpenses", total);
        json.put("expenses", expenses);
        return json;
    }

    public List<Map<String, Object>> customerLedger(CurrentUser user, String customerId) {
        var customer = customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(customerId, user.getShopId());
        if (customer.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        double balance = 0;
        for (var sale : saleRepository
                .findByShopIdAndIsDeletedFalseAndCustomerIdOrderByCreatedAtDesc(user.getShopId(), customerId)) {
            balance += sale.getTotalAmount();
            entries.add(entry(customerId, "SALE", sale.getTotalAmount(), sale.getCreatedAt(),
                    "Sale " + sale.getSaleId()));
            if (sale.getPaidAmount() > 0) {
                entries.add(entry(customerId, "PAYMENT", -sale.getPaidAmount(), sale.getCreatedAt(),
                        "Payment against " + sale.getSaleId()));
                balance -= sale.getPaidAmount();
            }
        }
        for (var e : ledgerRepository
                .findByShopIdAndCustomerIdAndIsDeletedFalseOrderByCreatedAtAsc(user.getShopId(), customerId)) {
            double delta = "CREDIT".equals(e.getType()) ? -e.getAmount() : e.getAmount();
            balance += delta;
            entries.add(entry(customerId, e.getType(), delta, e.getCreatedAt(), e.getNote()));
        }
        entries.sort((a, b) -> b.get("createdAt").toString().compareTo(a.get("createdAt").toString()));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("customerId", customerId);
        json.put("customerName", customer.get().getName());
        json.put("balance", balance);
        json.put("entries", entries);
        return List.of(json);
    }

    private Map<String, Object> entry(String customerId, String type, double amount,
                                      LocalDateTime createdAt, String note) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("customerId", customerId);
        json.put("type", type);
        json.put("amount", amount);
        json.put("note", note);
        json.put("createdAt", createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return json;
    }
}
