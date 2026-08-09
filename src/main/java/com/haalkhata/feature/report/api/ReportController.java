package com.haalkhata.feature.report.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.report.application.ReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/sales")
    public Map<String, Object> sales(@AuthenticationPrincipal CurrentUser user,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.salesSummary(user, from, to);
    }

    @GetMapping("/inventory")
    public List<Map<String, Object>> inventory(@AuthenticationPrincipal CurrentUser user) {
        return reportService.inventory(user);
    }

    @GetMapping("/expenses")
    public Map<String, Object> expenses(@AuthenticationPrincipal CurrentUser user,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.expenseReport(user, from, to);
    }

    @GetMapping("/customer-ledger")
    public List<Map<String, Object>> customerLedger(@AuthenticationPrincipal CurrentUser user,
                                                    @RequestParam String customerId) {
        return reportService.customerLedger(user, customerId);
    }
}
