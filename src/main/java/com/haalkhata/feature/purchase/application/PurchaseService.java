package com.haalkhata.feature.purchase.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.core.util.MoneyUtil;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.product.domain.ProductBatch;
import com.haalkhata.feature.product.infrastructure.ProductBatchRepository;
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
    private final ProductBatchRepository productBatchRepository;

    public record SupplierRequest(String supplierId, String name, String phone, Boolean isDeleted) {
    }

    public record PurchaseItemRequest(String productId, String name, double qty, double unitCost,
                                      String batchNumber, java.time.LocalDate expiryDate) {
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
        return purchaseRepository.findByShopIdAndBranchIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId(), user.getBranchId())
                .stream().map(this::purchaseJson).toList();
    }

    public Map<String, Object> get(CurrentUser user, String purchaseId) {
        return purchaseRepository.findByPurchaseIdAndShopIdAndBranchIdAndIsDeletedFalse(purchaseId, user.getShopId(), user.getBranchId())
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

        double calculatedTotal = 0;
        for (PurchaseItemRequest item : request.items()) {
            if (!Double.isFinite(item.qty()) || item.qty() <= 0) {
                throw BusinessException.badRequest("Quantity must be greater than zero for " + item.name());
            }
            if (item.qty() != Math.floor(item.qty())) {
                throw BusinessException.badRequest(
                        "Fractional quantity is not supported for " + item.name());
            }
            if (!Double.isFinite(item.unitCost()) || item.unitCost() < 0) {
                throw BusinessException.badRequest("Unit cost must be a valid non-negative amount for " + item.name());
            }
            calculatedTotal += item.qty() * item.unitCost();
        }
        double total = MoneyUtil.round2(calculatedTotal);
        if (request.totalAmount() != null
                && (!Double.isFinite(request.totalAmount())
                || Math.abs(MoneyUtil.round2(request.totalAmount()) - total) > 0.01)) {
            throw BusinessException.badRequest("Purchase total does not match the item lines");
        }
        double paidAmount = request.paidAmount() == null ? 0 : request.paidAmount();
        if (!Double.isFinite(paidAmount) || paidAmount < 0 || paidAmount > total + 0.005) {
            throw BusinessException.badRequest("Paid amount must be between zero and the purchase total");
        }
        LocalDateTime now = LocalDateTime.now();
        Purchase purchase = Purchase.builder()
                .purchaseId(request.purchaseId() == null || request.purchaseId().isBlank()
                        ? Ids.uuid() : request.purchaseId())
                .shopId(user.getShopId())
                .branchId(user.getBranchId())
                .staffId(user.getUserId())
                .supplierId(supplier.getSupplierId())
                .totalAmount(total)
                .paidAmount(MoneyUtil.round2(paidAmount))
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(false)
                .build();
        purchaseRepository.save(purchase);

        for (PurchaseItemRequest item : request.items()) {
            if (item.qty() <= 0) {
                throw BusinessException.badRequest("Quantity must be greater than zero for " + item.name());
            }
            if (item.qty() != Math.floor(item.qty())) {
                throw BusinessException.badRequest(
                        "Fractional quantity is not supported for " + item.name());
            }
            purchaseItemRepository.save(PurchaseItem.builder()
                    .id(Ids.uuid())
                    .purchaseId(purchase.getPurchaseId())
                    .productId(item.productId())
                    .name(item.name())
                    .qty(item.qty())
                    .unitCost(item.unitCost())
                    .batchNumber(item.batchNumber() == null ? null : item.batchNumber().trim())
                    .expiryDate(item.expiryDate())
                    .build());
            if (item.productId() != null && !item.productId().isBlank()) {
                productRepository.incrementStock(item.productId(), user.getShopId(), user.getBranchId(),
                        (int) item.qty(), now);
                productRepository.findByProductIdAndShopIdAndBranchIdAndIsDeletedFalse(item.productId(), user.getShopId(), user.getBranchId())
                        .ifPresent(product -> {
                            if (item.batchNumber() != null && !item.batchNumber().isBlank()) {
                                product.setBatchNumber(item.batchNumber().trim());
                            }
                            if (item.expiryDate() != null) product.setExpiryDate(item.expiryDate());
                            product.setUpdatedAt(now);
                            productRepository.save(product);
                        });
                if (item.batchNumber() != null && !item.batchNumber().isBlank()) {
                    ProductBatch batch = productBatchRepository.findByShopIdAndBranchIdAndProductIdAndIsDeletedFalseOrderByExpiryDateAsc(
                                    user.getShopId(), user.getBranchId(), item.productId()).stream()
                            .filter(b -> item.batchNumber().trim().equals(b.getBatchNumber())
                                    && java.util.Objects.equals(item.expiryDate(), b.getExpiryDate()))
                            .findFirst()
                            .orElseGet(() -> ProductBatch.builder()
                                    .batchId(Ids.uuid()).shopId(user.getShopId()).branchId(user.getBranchId()).productId(item.productId())
                                    .batchNumber(item.batchNumber().trim()).expiryDate(item.expiryDate())
                                    .quantity(0).unitCost(item.unitCost()).updatedAt(now).isDeleted(false).build());
                    batch.setQuantity(batch.getQuantity() + (int) item.qty());
                    batch.setUnitCost(item.unitCost());
                    batch.setUpdatedAt(now);
                    productBatchRepository.save(batch);
                }
            }
        }
        log.info("PURCHASE_CREATED purchaseId={} supplier={} total={}",
                purchase.getPurchaseId(), supplier.getName(), total);
        return purchaseJson(purchase);
    }

    @Transactional
    public void delete(CurrentUser user, String purchaseId) {
        Purchase purchase = purchaseRepository.findByPurchaseIdAndShopIdAndBranchIdAndIsDeletedFalse(
                        purchaseId, user.getShopId(), user.getBranchId())
                .orElseThrow(() -> BusinessException.notFound("Purchase not found"));
        LocalDateTime now = LocalDateTime.now();
        // Reverse the stock added by this purchase. If any item has since been
        // sold below the purchased quantity the whole delete is rolled back,
        // so stock never silently goes negative.
        for (PurchaseItem item : purchaseItemRepository.findByPurchaseId(purchaseId)) {
            if (item.getProductId() != null && !item.getProductId().isBlank()) {
                int affected = productRepository.decrementStock(item.getProductId(), user.getShopId(), user.getBranchId(),
                        (int) item.getQty(), now);
                if (affected == 0) {
                    throw BusinessException.badRequest("Cannot delete purchase: stock for "
                            + item.getName() + " has already been reduced below the purchased quantity");
                }
            }
        }
        purchase.setDeleted(true);
        purchase.setUpdatedAt(now);
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
                    m.put("batchNumber", i.getBatchNumber() == null ? "" : i.getBatchNumber());
                    m.put("expiryDate", i.getExpiryDate());
                    return m;
                }).toList();
        Map<String, Object> json = new HashMap<>();
        json.put("purchaseId", p.getPurchaseId());
        json.put("supplierId", p.getSupplierId());
        json.put("items", items);
        json.put("totalAmount", p.getTotalAmount());
        json.put("paidAmount", p.getPaidAmount());
        json.put("shopId", p.getShopId());
        json.put("branchId", p.getBranchId());
        json.put("staffId", p.getStaffId());
        json.put("createdAt", p.getCreatedAt().format(ISO));
        json.put("updatedAt", p.getUpdatedAt().format(ISO));
        json.put("isDeleted", p.isDeleted());
        return json;
    }
}
