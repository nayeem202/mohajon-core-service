package com.haalkhata.feature.vendor.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.vendor.application.VendorService;
import com.haalkhata.feature.vendor.application.VendorService.PaymentRequest;
import com.haalkhata.feature.vendor.application.VendorService.PurchaseRequest;
import com.haalkhata.feature.vendor.application.VendorService.UpsertVendorRequest;
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
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return vendorService.list(user);
    }

    @GetMapping("/{vendorId}")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user, @PathVariable String vendorId) {
        return vendorService.get(user, vendorId);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody UpsertVendorRequest request) {
        return vendorService.upsert(user, request);
    }

    @PutMapping("/{vendorId}")
    public Map<String, Object> update(@AuthenticationPrincipal CurrentUser user,
                                      @PathVariable String vendorId,
                                      @RequestBody UpsertVendorRequest request) {
        return vendorService.upsert(user, withId(request, vendorId));
    }

    @DeleteMapping("/{vendorId}")
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String vendorId) {
        vendorService.delete(user, vendorId);
    }

    @GetMapping("/{vendorId}/purchases")
    public List<Map<String, Object>> purchases(@AuthenticationPrincipal CurrentUser user,
                                               @PathVariable String vendorId) {
        return vendorService.purchases(user, vendorId);
    }

    @PostMapping("/{vendorId}/purchases")
    public Map<String, Object> addPurchase(@AuthenticationPrincipal CurrentUser user,
                                           @PathVariable String vendorId,
                                           @Valid @RequestBody PurchaseRequest request) {
        return vendorService.addPurchase(user, vendorId, request);
    }

    @GetMapping("/{vendorId}/payments")
    public List<Map<String, Object>> payments(@AuthenticationPrincipal CurrentUser user,
                                              @PathVariable String vendorId) {
        return vendorService.payments(user, vendorId);
    }

    @PostMapping("/{vendorId}/payments")
    public Map<String, Object> addPayment(@AuthenticationPrincipal CurrentUser user,
                                          @PathVariable String vendorId,
                                          @Valid @RequestBody PaymentRequest request) {
        return vendorService.addPayment(user, vendorId, request);
    }

    private UpsertVendorRequest withId(UpsertVendorRequest request, String vendorId) {
        return new UpsertVendorRequest(vendorId, request.companyName(), request.contactPerson(),
                request.phone(), request.email(), request.category(), request.address(),
                request.products(), request.rating(), request.totalOrders(), request.totalSpent(),
                request.status(), request.isDeleted());
    }
}
