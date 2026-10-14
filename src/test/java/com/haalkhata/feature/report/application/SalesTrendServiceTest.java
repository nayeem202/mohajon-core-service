package com.haalkhata.feature.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SalesTrendServiceTest {

    @Mock
    private SaleRepository saleRepository;

    private SalesTrendService service;

    @BeforeEach
    void setUp() {
        service = new SalesTrendService(saleRepository);
    }

    @Test
    void trend_returnsOnlyAggregatesAndComparesPreviousEqualLengthPeriod() {
        CurrentUser user = user("shop-1", "branch-2");
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(saleRepository.aggregateSalesTrend(
                "shop-1", "branch-2", from.atStartOfDay(), to.plusDays(1).atStartOfDay(), "month"))
                .thenReturn(List.<Object[]>of(
                        new Object[]{LocalDateTime.of(2026, 9, 1, 0, 0), 6200.0, 4L}));
        when(saleRepository.aggregateSalesTotals(
                "shop-1", "branch-2", LocalDate.of(2026, 8, 2).atStartOfDay(), from.atStartOfDay()))
                .thenReturn(new Object[]{5000.0, 3L});

        Map<String, Object> result = service.trend(user, from, to, "MONTH");

        assertThat(result).containsEntry("totalSales", 6200.0)
                .containsEntry("saleCount", 4L)
                .containsEntry("previousPeriodSales", 5000.0)
                .containsEntry("changePercent", 24.0);
        assertThat((List<?>) result.get("data")).hasSize(1);
        verify(saleRepository).aggregateSalesTrend(
                "shop-1", "branch-2", from.atStartOfDay(), to.plusDays(1).atStartOfDay(), "month");
    }

    @Test
    void trend_rejectsInvalidDatesAndUnsupportedGranularity() {
        CurrentUser user = user("shop-1", "shop-1-main");

        assertThrows(ResponseStatusException.class,
                () -> service.trend(user, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), "day"));
        assertThrows(ResponseStatusException.class,
                () -> service.trend(user, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), "year"));
    }

    private CurrentUser user(String shopId, String branchId) {
        return new CurrentUser("user-1", "owner@example.com", "Owner", shopId,
                "OWNER", List.of("OWNER"), branchId);
    }
}
