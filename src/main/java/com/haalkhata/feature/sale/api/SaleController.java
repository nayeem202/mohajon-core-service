package com.haalkhata.feature.sale.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.sale.application.SaleService;
import com.haalkhata.feature.sale.application.SaleService.CreateSaleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    public List<Map<String, Object>> history(@AuthenticationPrincipal CurrentUser user) {
        return saleService.history(user);
    }

    @GetMapping("/customer/{customerId}")
    public List<Map<String, Object>> historyForCustomer(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable String customerId) {
        return saleService.historyForCustomer(user, customerId);
    }

    @GetMapping("/{saleId}")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user, @PathVariable String saleId) {
        return saleService.get(user, saleId);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody CreateSaleRequest request) {
        return saleService.create(user, request);
    }

    @DeleteMapping("/{saleId}")
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String saleId) {
        saleService.delete(user, saleId);
    }
}
