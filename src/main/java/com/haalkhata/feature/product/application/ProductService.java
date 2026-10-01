package com.haalkhata.feature.product.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.product.domain.Product;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.guardian.application.GuardianService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final GuardianService guardianService;

    public record UpsertRequest(String productId, String nameEn, String nameBn, String barcode,
                                String category, String unit, String imagePath,
                                Double buyingPrice, Double purchasePrice, Double sellingPrice,
                                Integer stockQty, Integer lowStockAlert, LocalDate expiryDate,
                                Boolean isDeleted) {
    }

    public record StockAdjustRequest(int deltaQty) {
    }

    public List<Map<String, Object>> list(CurrentUser user) {
        return productRepository.findByShopIdAndIsDeletedFalseOrderByNameEnAsc(user.getShopId())
                .stream().map(this::toJson).toList();
    }

    public Map<String, Object> get(CurrentUser user, String productId) {
        return productRepository.findByProductIdAndShopIdAndIsDeletedFalse(productId, user.getShopId())
                .map(this::toJson)
                .orElseThrow(() -> BusinessException.notFound("Product not found"));
    }

    public List<Map<String, Object>> lowStock(CurrentUser user) {
        // Per-product threshold, falling back to a default of 10 units when a
        // product has not configured its own low-stock alert.
        return productRepository.findByShopIdAndIsDeletedFalseOrderByNameEnAsc(user.getShopId())
                .stream()
                .filter(p -> {
                    int threshold = p.getLowStockAlert() > 0 ? p.getLowStockAlert() : 10;
                    return p.getStockQty() <= threshold;
                })
                .map(this::toJson)
                .toList();
    }

    @Transactional
    public Map<String, Object> upsert(CurrentUser user, UpsertRequest request) {
        Product product;
        boolean isNew = request.productId() == null || request.productId().isBlank();
        if (isNew) {
            product = Product.builder()
                    .productId(Ids.uuid())
                    .shopId(user.getShopId())
                    .buyingPrice(0)
                    .purchasePrice(0)
                    .sellingPrice(0)
                    .stockQty(0)
                    .lowStockAlert(0)
                    .isDeleted(false)
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            product = productRepository.findById(request.productId())
                    .orElseThrow(() -> BusinessException.notFound("Product not found"));
            if (!product.getShopId().equals(user.getShopId())) {
                throw BusinessException.forbidden("You do not own this product");
            }
        }
        product.setNameEn(request.nameEn() == null ? product.getNameEn() : request.nameEn().trim());
        product.setNameBn(request.nameBn());
        product.setBarcode(request.barcode());
        product.setCategory(request.category());
        product.setUnit(request.unit());
        product.setImagePath(request.imagePath());
        product.setBuyingPrice(request.buyingPrice() == null ? product.getBuyingPrice() : request.buyingPrice());
        product.setPurchasePrice(request.purchasePrice() == null ? product.getPurchasePrice() : request.purchasePrice());
        product.setSellingPrice(request.sellingPrice() == null ? product.getSellingPrice() : request.sellingPrice());
        product.setStockQty(request.stockQty() == null ? product.getStockQty() : request.stockQty());
        product.setLowStockAlert(request.lowStockAlert() == null ? product.getLowStockAlert() : request.lowStockAlert());
        if (request.expiryDate() != null) {
            product.setExpiryDate(request.expiryDate());
        }
        product.setDeleted(request.isDeleted() != null && request.isDeleted());
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        log.info("PRODUCT_{} productId={} shopId={} name={}",
                isNew ? "CREATED" : "UPDATED", product.getProductId(), user.getShopId(), product.getNameEn());
        guardianService.record(user, isNew ? "PRODUCT_CREATED" : "PRODUCT_UPDATED", "PRODUCT", product.getProductId(), null,
                Map.of("name", product.getNameEn(), "stockQty", product.getStockQty()));
        return toJson(product);
    }

    @Transactional
    public Map<String, Object> adjustStock(CurrentUser user, String productId, StockAdjustRequest request) {
        Product product = productRepository.findByProductIdAndShopIdAndIsDeletedFalse(productId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Product not found"));
        int newQty = product.getStockQty() + request.deltaQty();
        if (newQty < 0) {
            throw BusinessException.badRequest("Insufficient stock");
        }
        product.setStockQty(newQty);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        log.info("PRODUCT_STOCK_ADJUSTED productId={} delta={} newQty={}", productId, request.deltaQty(), newQty);
        guardianService.record(user, "STOCK_ADJUSTED", "PRODUCT", productId, null,
                Map.of("deltaQty", request.deltaQty(), "newQty", newQty));
        return toJson(product);
    }

    @Transactional
    public void delete(CurrentUser user, String productId) {
        Product product = productRepository.findByProductIdAndShopIdAndIsDeletedFalse(productId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Product not found"));
        product.setDeleted(true);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        log.info("PRODUCT_DELETED productId={} shopId={}", productId, user.getShopId());
        guardianService.record(user, "DELETE", "PRODUCT", productId, null, Map.of("reason", "manual_delete"));
    }

    public Map<String, Object> toJson(Product p) {
        Map<String, Object> json = new HashMap<>();
        json.put("productId", p.getProductId());
        json.put("nameEn", p.getNameEn());
        json.put("nameBn", p.getNameBn());
        json.put("barcode", p.getBarcode());
        json.put("category", p.getCategory());
        json.put("unit", p.getUnit());
        json.put("imagePath", p.getImagePath());
        json.put("buyingPrice", p.getBuyingPrice());
        json.put("purchasePrice", p.getPurchasePrice());
        json.put("sellingPrice", p.getSellingPrice());
        json.put("stockQty", p.getStockQty());
        json.put("lowStockAlert", p.getLowStockAlert());
        json.put("expiryDate", p.getExpiryDate() == null ? null : p.getExpiryDate().toString());
        json.put("shopId", p.getShopId());
        json.put("updatedAt", p.getUpdatedAt().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        json.put("isDeleted", p.isDeleted());
        return json;
    }
}
