package com.haalkhata.feature.sale.returning.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.sale.returning.application.SaleReturnService;
import com.haalkhata.feature.sale.returning.application.SaleReturnService.CreateReturnRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sale-returns")
@RequiredArgsConstructor
public class SaleReturnController {
    private final SaleReturnService saleReturnService;

    @GetMapping
    public List<Map<String, Object>> history(@AuthenticationPrincipal CurrentUser user) {
        return saleReturnService.history(user);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody CreateReturnRequest request) {
        return saleReturnService.create(user, request);
    }
}
