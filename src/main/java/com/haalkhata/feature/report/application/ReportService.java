package com.haalkhata.feature.report.application;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.customer.domain.Customer;
import com.haalkhata.feature.customer.infrastructure.CustomerLedgerEntryRepository;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.expense.domain.Expense;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.domain.SaleItem;
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
    private final PaymentRepository paymentRepository;

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

    // ---------------------------------------------------------------- export tables

    public ReportTable salesSummaryTable(CurrentUser user, LocalDate from, LocalDate to) {
        LocalDateTime[] r = range(from, to);
        List<Map<String, Object>> rows = salesInRange(user, r).stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", dateStr(s.getCreatedAt()));
            row.put("invoice", s.getSaleId());
            row.put("customer", s.getCustomerId());
            row.put("discount", money(s.getDiscount()));
            row.put("vat", money(s.getVatPercent()));
            row.put("total", money(s.getTotalAmount()));
            row.put("paid", money(s.getPaidAmount()));
            row.put("due", money(s.getDueAmount()));
            return row;
        }).toList();
        return new ReportTable("Sales Summary", rangeSubtitle(r), List.of(
                new ReportColumn("date", "Date", 75, false),
                new ReportColumn("invoice", "Invoice", 120, false),
                new ReportColumn("customer", "Customer", 140, false),
                new ReportColumn("discount", "Discount", 95, true),
                new ReportColumn("vat", "VAT%", 65, true),
                new ReportColumn("total", "Total", 95, true),
                new ReportColumn("paid", "Paid", 95, true),
                new ReportColumn("due", "Due", 97, true)), rows);
    }

    public ReportTable inventoryTable(CurrentUser user) {
        List<Map<String, Object>> rows = productRepository
                .findByShopIdAndIsDeletedFalseOrderByNameEnAsc(user.getShopId()).stream().map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sku", p.getProductId());
                    row.put("name", p.getNameEn());
                    row.put("barcode", p.getBarcode());
                    row.put("category", p.getCategory());
                    row.put("stock", p.getStockQty());
                    row.put("buyPrice", money(p.getBuyingPrice()));
                    row.put("cost", money(p.getPurchasePrice()));
                    row.put("sellPrice", money(p.getSellingPrice()));
                    row.put("value", money(p.getStockQty() * p.getPurchasePrice()));
                    return row;
                }).toList();
        return new ReportTable("Inventory Report", "As of " + LocalDate.now(),
                List.of(
                        new ReportColumn("sku", "SKU", 100, false),
                        new ReportColumn("name", "Name", 170, false),
                        new ReportColumn("barcode", "Barcode", 100, false),
                        new ReportColumn("category", "Category", 90, false),
                        new ReportColumn("stock", "Stock", 50, true),
                        new ReportColumn("buyPrice", "Buy Price", 60, true),
                        new ReportColumn("cost", "Cost", 60, true),
                        new ReportColumn("sellPrice", "Sell Price", 60, true),
                        new ReportColumn("value", "Value", 92, true)),
                rows);
    }

    public ReportTable expenseTable(CurrentUser user, LocalDate from, LocalDate to) {
        LocalDateTime[] r = range(from, to);
        List<Expense> expenses = expenseRepository
                .findByShopIdAndIsDeletedFalseAndDateBetweenOrderByDateDesc(user.getShopId(), r[0], r[1]);
        double total = expenses.stream().mapToDouble(Expense::getAmount).sum();
        List<Map<String, Object>> rows = expenses.stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", dateStr(e.getDate()));
            row.put("description", e.getDescription());
            row.put("category", e.getCategoryLabel());
            row.put("amount", money(e.getAmount()));
            return row;
        }).toList();
        return new ReportTable("Expenses Report",
                rangeSubtitle(r) + " | Total: " + money(total),
                List.of(
                        new ReportColumn("date", "Date", 100, false),
                        new ReportColumn("description", "Description", 380, false),
                        new ReportColumn("category", "Category", 130, false),
                        new ReportColumn("amount", "Amount", 172, true)),
                rows);
    }

    public ReportTable customerLedgerTable(CurrentUser user, String customerId) {
        var customer = customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(customerId, user.getShopId());
        if (customer.isEmpty()) {
            return new ReportTable("Customer Ledger", "Customer not found", List.of(
                    new ReportColumn("customer", "Customer", 320, false),
                    new ReportColumn("phone", "Phone", 200, false),
                    new ReportColumn("balance", "Balance", 262, true)), List.of());
        }
        double balance = customerBalance(user.getShopId(), customerId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customer", customer.get().getName());
        row.put("phone", customer.get().getPhone() == null ? "" : customer.get().getPhone());
        row.put("balance", money(balance));
        return new ReportTable("Customer Ledger",
                "As of " + LocalDate.now() + " | " + customer.get().getName(),
                List.of(
                        new ReportColumn("customer", "Customer", 320, false),
                        new ReportColumn("phone", "Phone", 200, false),
                        new ReportColumn("balance", "Balance", 262, true)),
                List.of(row));
    }

    public ReportTable profitLossTable(CurrentUser user, LocalDate from, LocalDate to) {
        LocalDateTime[] r = range(from, to);
        List<Sale> sales = salesInRange(user, r);
        double revenue = sales.stream().mapToDouble(Sale::getTotalAmount).sum();
        double cogs = 0;
        for (Sale s : sales) {
            List<SaleItem> items = saleItemRepository.findBySaleId(s.getSaleId());
            cogs += items.stream().mapToDouble(i -> i.getQty() * i.getPurchasePrice()).sum();
        }
        double gross = revenue - cogs;
        double expenses = expenseRepository
                .findByShopIdAndIsDeletedFalseAndDateBetweenOrderByDateDesc(user.getShopId(), r[0], r[1]).stream()
                .mapToDouble(Expense::getAmount).sum();
        double net = gross - expenses;

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(plRow("Sales Revenue", revenue));
        rows.add(plRow("Cost of Goods Sold", -cogs));
        rows.add(plRow("Gross Profit", gross));
        rows.add(plRow("Operating Expenses", -expenses));
        rows.add(plRow("Net Profit", net));
        return new ReportTable("Profit & Loss", rangeSubtitle(r), List.of(
                new ReportColumn("item", "Item", 500, false),
                new ReportColumn("amount", "Amount", 282, true)), rows);
    }

    public ReportTable productSalesTable(CurrentUser user, LocalDate from, LocalDate to) {
        LocalDateTime[] r = range(from, to);
        Map<String, ProductSalesAgg> agg = new LinkedHashMap<>();
        for (Sale s : salesInRange(user, r)) {
            for (SaleItem item : saleItemRepository.findBySaleId(s.getSaleId())) {
                String pid = item.getProductId() == null || item.getProductId().isBlank()
                        ? "__unknown__" : item.getProductId();
                ProductSalesAgg a = agg.computeIfAbsent(pid, k -> new ProductSalesAgg(item.getName()));
                a.qty += item.getQty();
                a.revenue += item.getQty() * item.getUnitPrice();
                a.profit += item.getQty() * (item.getUnitPrice() - item.getPurchasePrice());
            }
        }
        List<Map<String, Object>> rows = agg.values().stream()
                .sorted((a, b) -> Double.compare(b.revenue, a.revenue))
                .map(a -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("product", a.name);
                    row.put("qty", a.qty);
                    row.put("revenue", money(a.revenue));
                    row.put("profit", money(a.profit));
                    return row;
                }).toList();
        return new ReportTable("Product Sales", rangeSubtitle(r), List.of(
                new ReportColumn("product", "Product", 440, false),
                new ReportColumn("qty", "Quantity", 110, true),
                new ReportColumn("revenue", "Revenue", 116, true),
                new ReportColumn("profit", "Profit", 116, true)), rows);
    }

    public ReportTable dailyTransactionsTable(CurrentUser user, LocalDate from, LocalDate to) {
        LocalDateTime[] r = range(from, to);
        Map<LocalDate, double[]> byDay = new LinkedHashMap<>();
        for (Sale s : salesInRange(user, r)) {
            double[] v = byDay.computeIfAbsent(s.getCreatedAt().toLocalDate(), k -> new double[3]);
            v[0]++;
            v[1] += s.getTotalAmount();
            v[2] += s.getPaidAmount();
        }
        List<Map<String, Object>> rows = byDay.entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", e.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    row.put("sales", (int) e.getValue()[0]);
                    row.put("amount", money(e.getValue()[1]));
                    row.put("received", money(e.getValue()[2]));
                    return row;
                }).toList();
        return new ReportTable("Daily Transactions", rangeSubtitle(r), List.of(
                new ReportColumn("date", "Date", 200, false),
                new ReportColumn("sales", "Sales", 140, true),
                new ReportColumn("amount", "Sales Amount", 220, true),
                new ReportColumn("received", "Received", 222, true)), rows);
    }

    public ReportTable paymentMethodsTable(CurrentUser user, LocalDate from, LocalDate to) {
        LocalDateTime[] r = range(from, to);
        Map<String, double[]> agg = new LinkedHashMap<>();
        for (var p : paymentRepository
                .findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId()).stream()
                .filter(p -> !p.getCreatedAt().isBefore(r[0]) && p.getCreatedAt().isBefore(r[1])).toList()) {
            double[] v = agg.computeIfAbsent(p.getPaymentMethod(), k -> new double[2]);
            v[0]++;
            v[1] += p.getAmount();
        }
        double walkInSum = 0;
        int walkInCount = 0;
        for (Sale s : salesInRange(user, r)) {
            String cid = s.getCustomerId();
            if (cid == null || cid.trim().isEmpty()) {
                walkInSum += s.getPaidAmount();
                walkInCount++;
            }
        }
        agg.put("Cash (Walk-in)", new double[] {walkInCount, walkInSum});

        double totalCount = 0;
        double totalAmount = 0;
        for (double[] v : agg.values()) {
            totalCount += v[0];
            totalAmount += v[1];
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        agg.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .forEach(e -> rows.add(pmRow(e.getKey(), e.getValue()[0], e.getValue()[1])));
        rows.add(pmRow("Total", totalCount, totalAmount));
        return new ReportTable("Payment Methods", rangeSubtitle(r), List.of(
                new ReportColumn("method", "Method", 400, false),
                new ReportColumn("payments", "Payments", 130, true),
                new ReportColumn("amount", "Amount", 252, true)), rows);
    }

    public ReportTable duePaymentsTable(CurrentUser user) {
        Map<String, double[]> agg = new LinkedHashMap<>();
        for (Sale s : saleRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId())) {
            if (s.getDueAmount() <= 0) {
                continue;
            }
            double[] v = agg.computeIfAbsent(s.getCustomerId(), k -> new double[2]);
            v[0] += s.getDueAmount();
            v[1]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        agg.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> {
                    var c = customerRepository
                            .findByCustomerIdAndShopIdAndIsDeletedFalse(e.getKey(), user.getShopId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("customer", c.map(Customer::getName).orElse("Unknown"));
                    row.put("phone", c.map(Customer::getPhone).orElse(""));
                    row.put("due", money(e.getValue()[0]));
                    row.put("openSales", (int) e.getValue()[1]);
                    rows.add(row);
                });
        return new ReportTable("Due Payments", "As of " + LocalDate.now(), List.of(
                new ReportColumn("customer", "Customer", 300, false),
                new ReportColumn("phone", "Phone", 180, false),
                new ReportColumn("due", "Due Amount", 172, true),
                new ReportColumn("openSales", "Open Sales", 130, true)), rows);
    }

    public ReportTable vatTable(CurrentUser user, LocalDate from, LocalDate to) {
        LocalDateTime[] r = range(from, to);
        List<Map<String, Object>> rows = new ArrayList<>();
        double totalVat = 0;
        for (Sale s : salesInRange(user, r)) {
            if (s.getVatPercent() <= 0) {
                continue;
            }
            double subtotal = saleItemRepository.findBySaleId(s.getSaleId()).stream()
                    .mapToDouble(i -> i.getQty() * i.getUnitPrice()).sum();
            double vatAmount = subtotal * s.getVatPercent() / 100.0;
            totalVat += vatAmount;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", dateStr(s.getCreatedAt()));
            row.put("invoice", s.getSaleId());
            row.put("customer", s.getCustomerId());
            row.put("subtotal", money(subtotal));
            row.put("vat", money(s.getVatPercent()));
            row.put("vatAmount", money(vatAmount));
            rows.add(row);
        }
        Map<String, Object> totalRow = new LinkedHashMap<>();
        totalRow.put("date", "");
        totalRow.put("invoice", "");
        totalRow.put("customer", "Total VAT");
        totalRow.put("subtotal", "");
        totalRow.put("vat", "");
        totalRow.put("vatAmount", money(totalVat));
        rows.add(totalRow);
        return new ReportTable("VAT Report", rangeSubtitle(r), List.of(
                new ReportColumn("date", "Date", 90, false),
                new ReportColumn("invoice", "Invoice", 130, false),
                new ReportColumn("customer", "Customer", 160, false),
                new ReportColumn("subtotal", "Subtotal", 130, true),
                new ReportColumn("vat", "VAT%", 90, true),
                new ReportColumn("vatAmount", "VAT Amount", 182, true)), rows);
    }

    // ---------------------------------------------------------------- private helpers

    private LocalDateTime[] range(LocalDate from, LocalDate to) {
        LocalDateTime start = (from == null ? LocalDate.of(2000, 1, 1) : from).atStartOfDay();
        LocalDateTime end = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();
        return new LocalDateTime[] {start, end};
    }

    private List<Sale> salesInRange(CurrentUser user, LocalDateTime[] r) {
        return saleRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId()).stream()
                .filter(s -> !s.getCreatedAt().isBefore(r[0]) && s.getCreatedAt().isBefore(r[1]))
                .toList();
    }

    private String rangeSubtitle(LocalDateTime[] r) {
        return r[0].format(DateTimeFormatter.ISO_LOCAL_DATE) + " to "
                + r[1].minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String dateStr(LocalDateTime t) {
        return t.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String money(double v) {
        return String.format("%.2f", v);
    }

    private double customerBalance(String shopId, String customerId) {
        double balance = 0;
        for (var sale : saleRepository
                .findByShopIdAndIsDeletedFalseAndCustomerIdOrderByCreatedAtDesc(shopId, customerId)) {
            balance += sale.getTotalAmount() - sale.getPaidAmount();
        }
        for (var e : ledgerRepository
                .findByShopIdAndCustomerIdAndIsDeletedFalseOrderByCreatedAtAsc(shopId, customerId)) {
            balance += "CREDIT".equals(e.getType()) ? -e.getAmount() : e.getAmount();
        }
        return balance;
    }

    private Map<String, Object> plRow(String item, double amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("item", item);
        row.put("amount", money(amount));
        return row;
    }

    private Map<String, Object> pmRow(String method, double count, double amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("method", method);
        row.put("payments", (int) count);
        row.put("amount", money(amount));
        return row;
    }

    private static class ProductSalesAgg {
        final String name;
        double qty;
        double revenue;
        double profit;

        ProductSalesAgg(String name) {
            this.name = name;
        }
    }
}
