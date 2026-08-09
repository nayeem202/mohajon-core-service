package com.haalkhata.feature.product.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.product.application.ProductService;
import com.haalkhata.feature.product.application.ProductService.StockAdjustRequest;
import com.haalkhata.feature.product.application.ProductService.UpsertRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return productService.list(user);
    }

    @GetMapping("/low-stock")
    public List<Map<String, Object>> lowStock(@AuthenticationPrincipal CurrentUser user) {
        return productService.lowStock(user);
    }

    @GetMapping("/{productId}")
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user, @PathVariable String productId) {
        return productService.get(user, productId);
    }

    @PostMapping
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user,
                                      @Valid @RequestBody UpsertRequest request) {
        return productService.upsert(user, request);
    }

    @PutMapping("/{productId}")
    public Map<String, Object> update(@AuthenticationPrincipal CurrentUser user,
                                      @PathVariable String productId,
                                      @RequestBody UpsertRequest request) {
        return productService.upsert(user, withId(request, productId));
    }

    @PatchMapping("/{productId}/stock")
    public Map<String, Object> adjustStock(@AuthenticationPrincipal CurrentUser user,
                                           @PathVariable String productId,
                                           @Valid @RequestBody StockAdjustRequest request) {
        return productService.adjustStock(user, productId, request);
    }

    @DeleteMapping("/{productId}")
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String productId) {
        productService.delete(user, productId);
    }

    private UpsertRequest withId(UpsertRequest request, String productId) {
        return new UpsertRequest(productId, request.nameEn(), request.nameBn(), request.barcode(),
                request.category(), request.unit(), request.imagePath(), request.buyingPrice(),
                request.purchasePrice(), request.sellingPrice(), request.stockQty(),
                request.lowStockAlert(), request.isDeleted());
    }
}
