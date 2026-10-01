package com.haalkhata.feature.purchase.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.purchase.application.PurchaseOrderService;
import com.haalkhata.feature.purchase.application.PurchaseOrderService.CreateRequest;
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
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return purchaseOrderService.list(user);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody CreateRequest request) {
        return purchaseOrderService.create(user, request);
    }
}
