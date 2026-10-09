package com.haalkhata.feature.report.api;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.report.application.ReportExportService;
import com.haalkhata.feature.report.application.ReportService;
import com.haalkhata.feature.report.application.ReportTable;
import com.haalkhata.feature.subscription.application.SubscriptionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;
    private final SubscriptionService subscriptionService;
    private final AppProperties props;

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

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal CurrentUser user,
                                         @PathVariable String type,
                                         @RequestParam(defaultValue = "pdf") String format,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String customerId) {
        if (props.isEnforceSubscription()) {
            subscriptionService.requireTier(user.getShopId(), SubscriptionService.Tier.PRO);
        }
        if ("customer-ledger".equals(type)
                && (customerId == null || customerId.isBlank())) {
            return ResponseEntity.badRequest()
                    .body("customerId is required for customer-ledger export".getBytes());
        }
        ReportTable table = switch (type) {
            case "sales" -> reportService.salesSummaryTable(user, from, to);
            case "inventory" -> reportService.inventoryTable(user);
            case "expenses" -> reportService.expenseTable(user, from, to);
            case "customer-ledger" -> reportService.customerLedgerTable(user, customerId);
            case "profit-loss" -> reportService.profitLossTable(user, from, to);
            case "product-sales" -> reportService.productSalesTable(user, from, to);
            case "daily-transactions" -> reportService.dailyTransactionsTable(user, from, to);
            case "payment-methods" -> reportService.paymentMethodsTable(user, from, to);
            case "due-payments" -> reportService.duePaymentsTable(user);
            case "vat" -> reportService.vatTable(user, from, to);
            default -> null;
        };
        if (table == null) {
            return ResponseEntity.status(404).body("Unknown report type".getBytes());
        }
        return buildFileResponse(type, format, table);
    }

    private ResponseEntity<byte[]> buildFileResponse(String type, String format, ReportTable table) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        byte[] bytes = xlsx ? reportExportService.exportXlsx(table) : reportExportService.exportPdf(table);
        String ext = xlsx ? "xlsx" : "pdf";
        MediaType mediaType = xlsx
                ? MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(type + "-report." + ext)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }
}
