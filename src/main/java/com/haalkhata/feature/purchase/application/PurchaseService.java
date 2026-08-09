package com.haalkhata.feature.purchase.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.product.domain.Product;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.purchase.domain.Purchase;
import com.haalkhata.feature.purchase.domain.PurchaseItem;
import com.haalkhata.feature.purchase.domain.Supplier;
import com.haalkhata.feature.purchase.infrastructure.PurchaseItemRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseRepository;
import com.haalkhata.feature.purchase.infrastructure.SupplierRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
public class PurchaseService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public record SupplierRequest(String supplierId, String name, String phone, Boolean isDeleted) {
    }

    public record PurchaseItemRequest(String productId, String name, double qty, double unitCost) {
    }

    public record CreatePurchaseRequest(String purchaseId, String supplierId,
                                        List<PurchaseItemRequest> items,
                                        Double totalAmount, Double paidAmount) {
    }

    // -------- Suppliers --------

    public List<Map<String, Object>> listSuppliers(CurrentUser user) {
        return supplierRepository.findByShopIdAndIsDeletedFalseOrderByNameAsc(user.getShopId())
                .stream().map(this::supplierJson).toList();
    }

    @Transactional
    public Map<String, Object> upsertSupplier(CurrentUser user, SupplierRequest request) {
        Supplier supplier;
        boolean isNew = request.supplierId() == null || request.supplierId().isBlank();
        if (isNew) {
            supplier = Supplier.builder()
                    .supplierId(Ids.uuid())
                    .shopId(user.getShopId())
                    .isDeleted(false)
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            supplier = supplierRepository.findBySupplierIdAndShopIdAndIsDeletedFalse(request.supplierId(), user.getShopId())
                    .orElseThrow(() -> BusinessException.notFound("Supplier not found"));
        }
        supplier.setName(request.name() == null ? supplier.getName() : request.name().trim());
        supplier.setPhone(request.phone());
        supplier.setDeleted(request.isDeleted() != null && request.isDeleted());
        supplier.setUpdatedAt(LocalDateTime.now());
        supplierRepository.save(supplier);
        log.info("SUPPLIER_{} supplierId={} shopId={}", isNew ? "CREATED" : "UPDATED",
                supplier.getSupplierId(), user.getShopId());
        return supplierJson(supplier);
    }

    @Transactional
    public void deleteSupplier(CurrentUser user, String supplierId) {
        Supplier supplier = supplierRepository.findBySupplierIdAndShopIdAndIsDeletedFalse(supplierId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Supplier not found"));
        supplier.setDeleted(true);
        supplier.setUpdatedAt(LocalDateTime.now());
        supplierRepository.save(supplier);
        log.info("SUPPLIER_DELETED supplierId={} shopId={}", supplierId, user.getShopId());
    }

    // -------- Purchases --------

    public List<Map<String, Object>> list(CurrentUser user) {
        return purchaseRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId())
                .stream().map(this::purchaseJson).toList();
    }

    public Map<String, Object> get(CurrentUser user, String purchaseId) {
        return purchaseRepository.findByPurchaseIdAndShopIdAndIsDeletedFalse(purchaseId, user.getShopId())
                .map(this::purchaseJson)
                .orElseThrow(() -> BusinessException.notFound("Purchase not found"));
    }

    @Transactional
    public Map<String, Object> create(CurrentUser user, CreatePurchaseRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw BusinessException.badRequest("Purchase must contain at least one item");
        }
        Supplier supplier = supplierRepository
                .findBySupplierIdAndShopIdAndIsDeletedFalse(request.supplierId(), user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Supplier not found"));

        double total = request.totalAmount() == null ? 0 : request.totalAmount();
        LocalDateTime now = LocalDateTime.now();
        Purchase purchase = Purchase.builder()
                .purchaseId(request.purchaseId() == null || request.purchaseId().isBlank()
                        ? Ids.uuid() : request.purchaseId())
                .shopId(user.getShopId())
                .supplierId(supplier.getSupplierId())
                .totalAmount(total)
                .paidAmount(request.paidAmount() == null ? 0 : request.paidAmount())
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(false)
                .build();
        purchaseRepository.save(purchase);

        for (PurchaseItemRequest item : request.items()) {
            purchaseItemRepository.save(PurchaseItem.builder()
                    .id(Ids.uuid())
                    .purchaseId(purchase.getPurchaseId())
                    .productId(item.productId())
                    .name(item.name())
                    .qty(item.qty())
                    .unitCost(item.unitCost())
                    .build());
            if (item.productId() != null) {
                productRepository.findByProductIdAndShopIdAndIsDeletedFalse(item.productId(), user.getShopId())
                        .ifPresent(product -> {
                            product.setStockQty(product.getStockQty() + (int) item.qty());
                            product.setUpdatedAt(now);
                            productRepository.save(product);
                        });
            }
        }
        log.info("PURCHASE_CREATED purchaseId={} supplier={} total={}",
                purchase.getPurchaseId(), supplier.getName(), total);
        return purchaseJson(purchase);
    }

    @Transactional
    public void delete(CurrentUser user, String purchaseId) {
        Purchase purchase = purchaseRepository.findByPurchaseIdAndShopIdAndIsDeletedFalse(purchaseId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Purchase not found"));
        purchase.setDeleted(true);
        purchase.setUpdatedAt(LocalDateTime.now());
        purchaseRepository.save(purchase);
        log.info("PURCHASE_DELETED purchaseId={} shopId={}", purchaseId, user.getShopId());
    }

    private Map<String, Object> supplierJson(Supplier s) {
        Map<String, Object> json = new HashMap<>();
        json.put("supplierId", s.getSupplierId());
        json.put("name", s.getName());
        json.put("phone", s.getPhone());
        json.put("shopId", s.getShopId());
        json.put("updatedAt", s.getUpdatedAt().format(ISO));
        json.put("isDeleted", s.isDeleted());
        return json;
    }

    private Map<String, Object> purchaseJson(Purchase p) {
        List<Map<String, Object>> items = purchaseItemRepository.findByPurchaseId(p.getPurchaseId())
                .stream().map(i -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", i.getProductId());
                    m.put("name", i.getName());
                    m.put("qty", i.getQty());
                    m.put("unitCost", i.getUnitCost());
                    return m;
                }).toList();
        Map<String, Object> json = new HashMap<>();
        json.put("purchaseId", p.getPurchaseId());
        json.put("supplierId", p.getSupplierId());
        json.put("items", items);
        json.put("totalAmount", p.getTotalAmount());
        json.put("paidAmount", p.getPaidAmount());
        json.put("shopId", p.getShopId());
        json.put("createdAt", p.getCreatedAt().format(ISO));
        json.put("updatedAt", p.getUpdatedAt().format(ISO));
        json.put("isDeleted", p.isDeleted());
        return json;
    }
}
