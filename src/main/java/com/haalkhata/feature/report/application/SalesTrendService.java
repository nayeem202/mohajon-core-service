package com.haalkhata.feature.report.application;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Efficient, branch-scoped sales chart aggregation for the app dashboard. */
@Service
@RequiredArgsConstructor
public class SalesTrendService {

    private static final long MAX_RANGE_DAYS = 3660;
    private static final Map<String, String> BUCKETS = Map.of(
            "day", "day",
            "week", "week",
            "month", "month");

    private final SaleRepository saleRepository;

    public Map<String, Object> trend(CurrentUser user, LocalDate from, LocalDate to, String groupBy) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid from/to date range is required.");
        }
        // Allow one local-calendar day beyond the server's UTC date boundary.
        if (to.isAfter(LocalDate.now().plusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The sales trend range cannot include future dates.");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The sales trend range is too large.");
        }
        String bucket = BUCKETS.get(groupBy == null ? "month" : groupBy.toLowerCase());
        if (bucket == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupBy must be day, week, or month.");
        }

        String shopId = user.getShopId();
        String branchId = user.getBranchId() == null || user.getBranchId().isBlank()
                ? shopId + "-main"
                : user.getBranchId();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
        List<Object[]> grouped = saleRepository.aggregateSalesTrend(
                shopId, branchId, start, endExclusive, bucket);
        List<Map<String, Object>> points = new ArrayList<>(grouped.size());
        double totalSales = 0;
        long saleCount = 0;
        for (Object[] row : grouped) {
            LocalDateTime period = toLocalDateTime(row[0]);
            double amount = asDouble(row[1]);
            long count = asLong(row[2]);
            totalSales += amount;
            saleCount += count;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("periodStart", period.toLocalDate().toString());
            point.put("totalSales", amount);
            point.put("saleCount", count);
            points.add(point);
        }

        LocalDate previousFrom = from.minusDays(days);
        Object[] previous = saleRepository.aggregateSalesTotals(
                shopId, branchId, previousFrom.atStartOfDay(), start);
        double previousSales = previous == null ? 0 : asDouble(previous[0]);
        long previousCount = previous == null ? 0 : asLong(previous[1]);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", from.toString());
        response.put("to", to.toString());
        response.put("groupBy", bucket);
        response.put("totalSales", totalSales);
        response.put("saleCount", saleCount);
        response.put("previousPeriodSales", previousSales);
        response.put("previousPeriodSaleCount", previousCount);
        response.put("changePercent", previousSales > 0
                ? ((totalSales - previousSales) / previousSales) * 100
                : null);
        response.put("data", points);
        return response;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalStateException("Unexpected date type in sales trend aggregation.");
    }

    private double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}
