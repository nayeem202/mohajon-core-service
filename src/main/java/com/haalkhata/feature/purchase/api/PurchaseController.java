package com.haalkhata.feature.purchase.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.purchase.application.PurchaseService;
import com.haalkhata.feature.purchase.application.PurchaseService.CreatePurchaseRequest;
import com.haalkhata.feature.purchase.application.PurchaseService.SupplierRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    // -------- Suppliers --------
    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers(@AuthenticationPrincipal CurrentUser user) {
        return purchaseService.listSuppliers(user);
    }

    @PostMapping("/suppliers")
    public Map<String, Object> createSupplier(@AuthenticationPrincipal CurrentUser user,
                                              @Valid @RequestBody SupplierRequest request) {
        return purchaseService.upsertSupplier(user, request);
    }

    @PutMapping("/suppliers/{supplierId}")
    public Map<String, Object> updateSupplier(@AuthenticationPrincipal CurrentUser user,
                                              @PathVariable String supplierId,
                                              @RequestBody SupplierRequest request) {
        return purchaseService.upsertSupplier(user, withSupplierId(request, supplierId));
    }

    @DeleteMapping("/suppliers/{supplierId}")
    public void deleteSupplier(@AuthenticationPrincipal CurrentUser user, @PathVariable String supplierId) {
        purchaseService.deleteSupplier(user, supplierId);
    }

    // -------- Purchases --------
    @GetMapping("/purchases")
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return purchaseService.list(user);
    }

    @GetMapping("/purchases/{purchaseId}")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user, @PathVariable String purchaseId) {
        return purchaseService.get(user, purchaseId);
    }

    @PostMapping("/purchases")
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody CreatePurchaseRequest request) {
        return purchaseService.create(user, request);
    }

    @DeleteMapping("/purchases/{purchaseId}")
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String purchaseId) {
        purchaseService.delete(user, purchaseId);
    }

    private SupplierRequest withSupplierId(SupplierRequest request, String supplierId) {
        return new SupplierRequest(supplierId, request.name(), request.phone(), request.isDeleted());
    }
}
