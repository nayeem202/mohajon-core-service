package com.haalkhata.feature.dashboard.application;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import java.time.DayOfWeek;
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
public class DashboardService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    public Map<String, Object> summary(CurrentUser user) {
        String shopId = user.getShopId();
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay();

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("todaySales", saleRepository.sumTotalForBranch(shopId, user.getBranchId(), dayStart, dayEnd));
        json.put("todayPaid", saleRepository.sumPaidForBranch(shopId, user.getBranchId(), dayStart, dayEnd));
        json.put("weekSales", saleRepository.sumTotalForBranch(shopId, user.getBranchId(), weekStart, dayEnd));
        json.put("totalDue", totalDue(user));
        json.put("customerCount", customerRepository.findByShopIdAndIsDeletedFalseOrderByNameAsc(shopId).size());
        Long lowStockCount = productRepository
                .findByShopIdAndIsDeletedFalseOrderByNameEnAsc(shopId).stream()
                .filter(p -> {
                    int threshold = p.getLowStockAlert() > 0 ? p.getLowStockAlert() : 10;
                    return p.getStockQty() <= threshold;
                })
                .count();
        json.put("lowStockCount", lowStockCount);
        json.put("productCount", productRepository.findByShopIdAndIsDeletedFalseOrderByNameEnAsc(shopId).size());
        json.put("weeklySales", weeklySales(user));
        json.put("topProducts", topProducts(user));
        return json;
    }

    public double totalDue(CurrentUser user) {
        return saleRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId()).stream()
                .filter(s -> user.getBranchId().equals(s.getBranchId()))
                .mapToDouble(Sale::getDueAmount).sum();
    }

    public List<Map<String, Object>> weeklySales(CurrentUser user) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            LocalDateTime from = day.atStartOfDay();
            LocalDateTime to = day.plusDays(1).atStartOfDay();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", day.format(DateTimeFormatter.ISO_LOCAL_DATE));
            entry.put("total", saleRepository.sumTotalForBranch(user.getShopId(), user.getBranchId(), from, to));
            result.add(entry);
        }
        return result;
    }

    public List<Map<String, Object>> topProducts(CurrentUser user) {
        Map<String, Map<String, Object>> byProduct = new HashMap<>();
        for (Sale sale : saleRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId()).stream()
                .filter(s -> user.getBranchId().equals(s.getBranchId())).toList()) {
            for (var item : saleItemRepository.findBySaleId(sale.getSaleId())) {
                Map<String, Object> agg = byProduct.computeIfAbsent(item.getProductId(), k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", item.getProductId());
                    m.put("name", item.getName());
                    m.put("qty", 0.0);
                    m.put("revenue", 0.0);
                    return m;
                });
                agg.put("qty", (Double) agg.get("qty") + item.getQty());
                agg.put("revenue", (Double) agg.get("revenue") + item.getQty() * item.getUnitPrice());
            }
        }
        return byProduct.values().stream()
                .sorted((a, b) -> Double.compare((Double) b.get("revenue"), (Double) a.get("revenue")))
                .limit(5)
                .toList();
    }
}
