package com.haalkhata.feature.sync.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.core.util.TimeUtil;
import com.haalkhata.feature.customer.domain.Customer;
import com.haalkhata.feature.customer.domain.CustomerLedgerEntry;
import com.haalkhata.feature.customer.infrastructure.CustomerLedgerEntryRepository;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.expense.domain.Expense;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.payment.domain.Payment;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.product.domain.Product;
import com.haalkhata.feature.product.domain.ProductBatch;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.product.infrastructure.ProductBatchRepository;
import com.haalkhata.feature.product.adjustment.domain.StockAdjustment;
import com.haalkhata.feature.product.adjustment.infrastructure.StockAdjustmentRepository;
import com.haalkhata.feature.purchase.domain.Purchase;
import com.haalkhata.feature.purchase.domain.PurchaseItem;
import com.haalkhata.feature.purchase.domain.Supplier;
import com.haalkhata.feature.purchase.infrastructure.PurchaseItemRepository;
import com.haalkhata.feature.purchase.domain.PurchaseOrder;
import com.haalkhata.feature.purchase.domain.PurchaseOrderItem;
import com.haalkhata.feature.purchase.infrastructure.PurchaseOrderItemRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseOrderRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseRepository;
import com.haalkhata.feature.purchase.infrastructure.SupplierRepository;
import com.haalkhata.feature.purchase.returning.domain.SupplierReturn;
import com.haalkhata.feature.purchase.returning.infrastructure.SupplierReturnRepository;
import com.haalkhata.feature.guardian.domain.GuardianAuditEvent;
import com.haalkhata.feature.guardian.infrastructure.GuardianAuditEventRepository;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.domain.SaleItem;
import com.haalkhata.feature.sale.domain.SaleItemBatchAllocation;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleItemBatchAllocationRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import com.haalkhata.feature.sale.returning.domain.SaleReturn;
import com.haalkhata.feature.sale.returning.domain.SaleReturnItem;
import com.haalkhata.feature.sale.returning.infrastructure.SaleReturnRepository;
import com.haalkhata.feature.sale.returning.infrastructure.SaleReturnItemRepository;
import com.haalkhata.feature.vendor.domain.Vendor;
import com.haalkhata.feature.vendor.domain.VendorPayment;
import com.haalkhata.feature.vendor.domain.VendorPurchase;
import com.haalkhata.feature.vendor.infrastructure.VendorPaymentRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorPurchaseRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final List<String> COLLECTIONS = List.of(
            "products", "productBatches", "customers", "sales", "customerLedgerEntries",
            "payments", "purchases", "suppliers", "vendors", "vendorPurchases",
            "vendorPayments", "expenses", "users", "supplierReturns", "saleReturns", "stockAdjustments", "auditEvents");

    private final ObjectMapper mapper;
    private final ProductRepository productRepository;
    private final ProductBatchRepository productBatchRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleItemBatchAllocationRepository saleItemBatchAllocationRepository;
    private final CustomerLedgerEntryRepository ledgerRepository;
    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseRepository expenseRepository;
    private final VendorRepository vendorRepository;
    private final VendorPurchaseRepository vendorPurchaseRepository;
    private final VendorPaymentRepository vendorPaymentRepository;
    private final SupplierReturnRepository supplierReturnRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final SaleReturnItemRepository saleReturnItemRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final GuardianAuditEventRepository guardianAuditEventRepository;

    public List<String> collections() {
        return COLLECTIONS;
    }

    @Transactional
    public int push(CurrentUser user, String collection, List<Map<String, Object>> documents) {
        if (!COLLECTIONS.contains(collection)) {
            throw BusinessException.badRequest("Unknown collection: " + collection);
        }
        int saved = 0;
        for (Map<String, Object> doc : documents) {
            String id = String.valueOf(doc.get(idKeyFor(collection)));
            if (id == null || id.isBlank() || "null".equals(id)) {
                log.warn("SYNC_PUSH_SKIP collection={} reason=missing-id doc={}", collection, doc);
                continue;
            }
            switch (collection) {
                case "products" -> upsertProduct(user, doc);
                case "customers" -> upsertCustomer(user.getShopId(), doc);
                case "sales" -> upsertSale(user, doc);
                case "customerLedgerEntries" -> upsertLedgerEntry(user.getShopId(), doc);
                case "payments" -> upsertPayment(user, doc);
                case "purchases" -> upsertPurchase(user, doc);
                case "suppliers" -> upsertSupplier(user.getShopId(), doc);
                case "vendors" -> upsertVendor(user.getShopId(), doc);
                case "vendorPurchases" -> upsertVendorPurchase(user.getShopId(), doc);
                case "vendorPayments" -> upsertVendorPayment(user.getShopId(), doc);
                case "expenses" -> upsertExpense(user, doc);
                case "users" -> upsertShopUser(user, doc);
                case "supplierReturns" -> upsertSupplierReturn(user, doc);
                case "saleReturns" -> upsertSaleReturn(user, doc);
                case "stockAdjustments" -> upsertStockAdjustment(user, doc);
                case "auditEvents" -> upsertAuditEvent(user, doc);
                case "productBatches" -> upsertProductBatch(user, doc);
            }
            saved++;
        }
        log.info("SYNC_PUSH collection={} shopId={} saved={}", collection, user.getShopId(), saved);
        return saved;
    }

    public record PullResult(List<Map<String, Object>> documents, LocalDateTime serverNow) {
    }

    /**
     * Delta pull. Every row returned has {@code updatedAt > after}. The
     * response watermark ({@code serverNow}) is captured BEFORE the query so
     * it is guaranteed to be <= any row that did not make it into this
     * response — clients can safely resume from it without skipping rows.
     * Rows are returned in ascending {@code updatedAt} order so client-side
     * paging over {@code hasMore} never skips entries with equal timestamps.
     */
    public PullResult pull(CurrentUser user, String collection, LocalDateTime after, Integer limit) {
        if (!COLLECTIONS.contains(collection)) {
            throw BusinessException.badRequest("Unknown collection: " + collection);
        }
        LocalDateTime cutoff = LocalDateTime.now();
        List<Map<String, Object>> docs = switch (collection) {
            case "products" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                productRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), user.getBranchId(), after)
                        .forEach(p -> d.add(productJson(p)));
                productRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), user.getBranchId(), after)
                        .forEach(p -> d.add(productJson(p)));
                yield d;
            }
            case "customers" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                customerRepository.findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), after)
                        .forEach(c -> d.add(customerJson(c)));
                customerRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(c -> d.add(customerJson(c)));
                yield d;
            }
            case "sales" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                saleRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), user.getBranchId(), after)
                        .forEach(s -> d.add(saleJson(s)));
                saleRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), user.getBranchId(), after)
                        .forEach(s -> d.add(saleJson(s)));
                yield d;
            }
            case "customerLedgerEntries" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                ledgerRepository.findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), after)
                        .forEach(e -> d.add(ledgerJson(e)));
                ledgerRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(e -> d.add(ledgerJson(e)));
                yield d;
            }
            case "payments" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                paymentRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), user.getBranchId(), after)
                        .forEach(p -> d.add(paymentJson(p)));
                paymentRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), user.getBranchId(), after)
                        .forEach(p -> d.add(paymentJson(p)));
                yield d;
            }
            case "purchases" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                purchaseRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), user.getBranchId(), after)
                        .forEach(p -> d.add(purchaseJson(p)));
                purchaseRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), user.getBranchId(), after)
                        .forEach(p -> d.add(purchaseJson(p)));
                yield d;
            }
            case "suppliers" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                supplierRepository.findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), after)
                        .forEach(s -> d.add(supplierJson(s)));
                supplierRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(s -> d.add(supplierJson(s)));
                yield d;
            }
            case "vendors" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                vendorRepository.findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), after)
                        .forEach(v -> d.add(vendorJson(v)));
                vendorRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(v -> d.add(vendorJson(v)));
                yield d;
            }
            case "vendorPurchases" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                vendorPurchaseRepository.findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), after)
                        .forEach(p -> d.add(vendorPurchaseJson(p)));
                vendorPurchaseRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(p -> d.add(vendorPurchaseJson(p)));
                yield d;
            }
            case "vendorPayments" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                vendorPaymentRepository.findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), after)
                        .forEach(p -> d.add(vendorPaymentJson(p)));
                vendorPaymentRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(p -> d.add(vendorPaymentJson(p)));
                yield d;
            }
            case "expenses" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                expenseRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), user.getBranchId(), after)
                        .forEach(e -> d.add(expenseJson(e)));
                expenseRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), user.getBranchId(), after)
                        .forEach(e -> d.add(expenseJson(e)));
                yield d;
            }
            case "supplierReturns" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                supplierReturnRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), user.getBranchId(), after)
                        .forEach(r -> d.add(supplierReturnJson(r)));
                supplierReturnRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), user.getBranchId(), after)
                        .forEach(r -> d.add(supplierReturnJson(r)));
                yield d;
            }
            case "saleReturns" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                saleReturnRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), user.getBranchId(), after)
                        .forEach(r -> d.add(saleReturnJson(r)));
                saleReturnRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), user.getBranchId(), after)
                        .forEach(r -> d.add(saleReturnJson(r)));
                yield d;
            }
            case "stockAdjustments" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                stockAdjustmentRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), user.getBranchId(), after)
                        .forEach(a -> d.add(stockAdjustmentJson(a)));
                stockAdjustmentRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), user.getBranchId(), after)
                        .forEach(a -> d.add(stockAdjustmentJson(a)));
                yield d;
            }
            case "auditEvents" -> guardianAuditEventRepository
                    .findByShopIdAndBranchIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(user.getShopId(), user.getBranchId(), after)
                    .stream().map(this::auditEventJson).toList();
            case "productBatches" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                productBatchRepository.findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(user.getShopId(), user.getBranchId(), after)
                        .forEach(b -> d.add(productBatchJson(b)));
                productBatchRepository.findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), user.getBranchId(), after)
                        .forEach(b -> d.add(productBatchJson(b)));
                yield d;
            }
            case "users" -> List.of(shopUserJson(user));
            default -> List.of();
        };
        // Deterministic ordering so `after`-based paging never skips or
        // duplicates rows between pages, and bound the in-memory payload to
        // the page size the client actually consumes.
        List<Map<String, Object>> paged = new ArrayList<>(docs);
        paged.sort(Comparator
                .comparing((Map<String, Object> d) ->
                        TimeUtil.parse(String.valueOf(d.get("updatedAt")), LocalDateTime.MIN))
                .thenComparing(d -> String.valueOf(d.get(idKeyFor(collection)))));
        int cap = limit != null && limit > 0 ? limit + 1 : Integer.MAX_VALUE;
        if (paged.size() > cap) {
            paged = new ArrayList<>(paged.subList(0, cap));
        }
        log.info("SYNC_PULL collection={} shopId={} after={} docs={}",
                collection, user.getShopId(), after, paged.size());
        return new PullResult(paged, cutoff);
    }

    // -------- upserts --------

    private void upsertProduct(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "productId");
        Product product = productRepository.findById(id).orElseGet(() -> Product.builder()
                .productId(id).shopId(shopId).branchId(user.getBranchId()).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(product.getShopId()) && product.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        if (product.getBranchId() != null && !user.getBranchId().equals(product.getBranchId())) {
            throw BusinessException.forbidden("Document belongs to another branch");
        }
        product.setShopId(shopId);
        product.setBranchId(user.getBranchId());
        product.setNameEn(str(doc, "nameEn"));
        product.setNameBn(nul(doc, "nameBn"));
        product.setBarcode(nul(doc, "barcode"));
        product.setSku(nul(doc, "sku"));
        product.setVariantName(nul(doc, "variantName"));
        product.setBatchNumber(nul(doc, "batchNumber"));
        product.setCategory(nul(doc, "category"));
        product.setUnit(nul(doc, "unit"));
        product.setImagePath(nul(doc, "imagePath"));
        product.setBuyingPrice(dbl(doc, "buyingPrice"));
        product.setPurchasePrice(dbl(doc, "purchasePrice"));
        product.setSellingPrice(dbl(doc, "sellingPrice"));
        int stock = doc.get("stockQty") == null ? 0 : ((Number) doc.get("stockQty")).intValue();
        if (stock < 0) {
            log.warn("SYNC_PRODUCT_STOCK_CLAMPED productId={} shopId={} stockQty={} -> 0", id, shopId, stock);
            stock = 0;
        }
        product.setStockQty(stock);
        product.setLowStockAlert(doc.get("lowStockAlert") == null ? 0 : ((Number) doc.get("lowStockAlert")).intValue());
        product.setExpiryDate(expiryFrom(doc));
        product.setDeleted(bool(doc, "isDeleted"));
        product.setUpdatedAt(time(doc, "updatedAt"));
        productRepository.save(product);
    }

    private void upsertCustomer(String shopId, Map<String, Object> doc) {
        String id = str(doc, "customerId");
        Customer customer = customerRepository.findById(id).orElseGet(() -> Customer.builder()
                .customerId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(customer.getShopId()) && customer.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        customer.setShopId(shopId);
        customer.setName(str(doc, "name"));
        customer.setPhone(nul(doc, "phone"));
        customer.setEmail(nul(doc, "email"));
        customer.setAddress(nul(doc, "address"));
        customer.setNotes(nul(doc, "notes"));
        customer.setImagePath(nul(doc, "imagePath"));
        customer.setTotalSpent(dbl(doc, "totalSpent"));
        customer.setTag(nul(doc, "tag"));
        customer.setCreditLimit(dbl(doc, "creditLimit"));
        if (doc.containsKey("loyaltyPoints")) {
            customer.setLoyaltyPoints(Math.max(0, intValue(doc, "loyaltyPoints")));
        }
        customer.setDeleted(bool(doc, "isDeleted"));
        customer.setUpdatedAt(time(doc, "updatedAt"));
        customerRepository.save(customer);
    }

    private void upsertSale(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "saleId");
        Sale sale = saleRepository.findById(id).orElseGet(() -> Sale.builder()
                .saleId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        int previousContribution = sale.isDeleted() ? 0
                : sale.getLoyaltyPointsEarned() - sale.getLoyaltyPointsRedeemed();
        if (!shopId.equals(sale.getShopId()) && sale.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        String payloadBranchId = nul(doc, "branchId");
        if (payloadBranchId != null && !user.getBranchId().equals(payloadBranchId)) {
            throw BusinessException.forbidden("Sale belongs to another branch");
        }
        if (sale.getBranchId() != null && !user.getBranchId().equals(sale.getBranchId())) {
            throw BusinessException.forbidden("Sale belongs to another branch");
        }
        sale.setShopId(shopId);
        sale.setInvoiceNumber(nul(doc, "invoiceNumber"));
        sale.setCustomerId(str(doc, "customerId"));
        sale.setDiscount(dbl(doc, "discount"));
        sale.setVatPercent(dbl(doc, "vatPercent"));
        sale.setCharge(dbl(doc, "charge"));
        sale.setTotalAmount(dbl(doc, "totalAmount"));
        sale.setPaidAmount(dbl(doc, "paidAmount"));
        sale.setDueAmount(dbl(doc, "dueAmount"));
        sale.setPaymentStatus(str(doc, "paymentStatus"));
        sale.setPaymentMethod(str(doc, "paymentMethod"));
        sale.setLoyaltyPointsRedeemed(Math.max(0, intValue(doc, "loyaltyPointsRedeemed")));
        sale.setLoyaltyPointsEarned(Math.max(0, intValue(doc, "loyaltyPointsEarned")));
        sale.setStaffId(nul(doc, "staffId"));
        sale.setBranchId(user.getBranchId());
        sale.setDiscountStaffId(nul(doc, "discountStaffId"));
        sale.setVoidedByStaffId(nul(doc, "voidedByStaffId"));
        sale.setVoidedAt(nullableTime(doc, "voidedAt"));
        sale.setCreatedAt(time(doc, "createdAt"));
        sale.setDeleted(bool(doc, "isDeleted"));
        sale.setUpdatedAt(time(doc, "updatedAt"));
        saleRepository.save(sale);
        int currentContribution = sale.isDeleted() ? 0
                : sale.getLoyaltyPointsEarned() - sale.getLoyaltyPointsRedeemed();
        int loyaltyDelta = currentContribution - previousContribution;
        if (loyaltyDelta != 0) {
            customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(
                    sale.getCustomerId(), shopId).ifPresent(customer -> {
                        customer.setLoyaltyPoints(Math.max(0, customer.getLoyaltyPoints() + loyaltyDelta));
                        customer.setUpdatedAt(LocalDateTime.now());
                        customerRepository.save(customer);
                    });
        }

        saleItemRepository.deleteBySaleId(id);
        Object items = doc.get("items");
        if (items instanceof List<?> list) {
            for (Object itemObj : list) {
                if (!(itemObj instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) itemMap;
                SaleItem saleItem = SaleItem.builder()
                        .id(nul(item, "saleItemId") == null || nul(item, "saleItemId").isBlank()
                                ? Ids.uuid() : nul(item, "saleItemId"))
                        .saleId(id)
                        .productId(nul(item, "productId"))
                        .name(str(item, "name"))
                        .qty(dbl(item, "qty"))
                        .unitPrice(dbl(item, "unitPrice"))
                        .purchasePrice(dbl(item, "purchasePrice"))
                        .build();
                saleItemRepository.save(saleItem);
                Object allocations = item.get("batchAllocations");
                if (allocations instanceof List<?> allocationList) {
                    for (Object allocationObj : allocationList) {
                        if (!(allocationObj instanceof Map<?, ?> allocationMap)) continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> allocation = (Map<String, Object>) allocationMap;
                        String batchId = nul(allocation, "batchId");
                        int quantity = allocation.get("quantity") instanceof Number n
                                ? n.intValue() : 0;
                        if (batchId == null || batchId.isBlank() || quantity <= 0) continue;
                        saleItemBatchAllocationRepository.save(SaleItemBatchAllocation.builder()
                                .allocationId(Ids.uuid())
                                .saleId(id)
                                .saleItemId(saleItem.getId())
                                .batchId(batchId)
                                .quantity(quantity)
                                .createdAt(time(doc, "updatedAt"))
                                .build());
                    }
                }
            }
        }
    }

    private void upsertLedgerEntry(String shopId, Map<String, Object> doc) {
        String id = str(doc, "entryId");
        CustomerLedgerEntry entry = ledgerRepository.findById(id).orElseGet(() -> CustomerLedgerEntry.builder()
                .entryId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(entry.getShopId()) && entry.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        entry.setShopId(shopId);
        entry.setCustomerId(str(doc, "customerId"));
        entry.setType(str(doc, "type"));
        entry.setAmount(dbl(doc, "amount"));
        entry.setNote(nul(doc, "note"));
        entry.setCreatedAt(time(doc, "createdAt"));
        entry.setDeleted(bool(doc, "isDeleted"));
        entry.setUpdatedAt(time(doc, "updatedAt"));
        ledgerRepository.save(entry);
    }

    private void upsertPayment(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "paymentId");
        Payment payment = paymentRepository.findById(id).orElseGet(() -> Payment.builder()
                .paymentId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(payment.getShopId()) && payment.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        String payloadBranchId = nul(doc, "branchId");
        if (payloadBranchId != null && !user.getBranchId().equals(payloadBranchId)) {
            throw BusinessException.forbidden("Payment belongs to another branch");
        }
        if (payment.getBranchId() != null && !user.getBranchId().equals(payment.getBranchId())) {
            throw BusinessException.forbidden("Payment belongs to another branch");
        }
        payment.setShopId(shopId);
        payment.setCustomerId(str(doc, "customerId"));
        payment.setSaleId(nul(doc, "saleId"));
        payment.setAmount(dbl(doc, "amount"));
        payment.setPaymentMethod(str(doc, "paymentMethod"));
        payment.setReference(nul(doc, "reference"));
        payment.setStaffId(nul(doc, "staffId"));
        payment.setBranchId(user.getBranchId());
        payment.setCreatedAt(time(doc, "createdAt"));
        payment.setDeleted(bool(doc, "isDeleted"));
        payment.setUpdatedAt(time(doc, "updatedAt"));
        paymentRepository.save(payment);
    }

    private void upsertPurchase(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "purchaseId");
        Purchase purchase = purchaseRepository.findById(id).orElseGet(() -> Purchase.builder()
                .purchaseId(id).shopId(shopId).branchId(user.getBranchId()).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(purchase.getShopId()) && purchase.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        if (purchase.getBranchId() != null && !user.getBranchId().equals(purchase.getBranchId())) {
            throw BusinessException.forbidden("Document belongs to another branch");
        }
        purchase.setShopId(shopId);
        purchase.setBranchId(user.getBranchId());
        // Older offline clients do not send staffId. Never erase an existing
        // attribution just because a legacy payload omitted the field.
        if (nul(doc, "staffId") != null) {
            purchase.setStaffId(nul(doc, "staffId"));
        }
        purchase.setSupplierId(str(doc, "supplierId"));
        purchase.setTotalAmount(dbl(doc, "totalAmount"));
        purchase.setPaidAmount(dbl(doc, "paidAmount"));
        purchase.setCreatedAt(time(doc, "createdAt"));
        purchase.setDeleted(bool(doc, "isDeleted"));
        purchase.setUpdatedAt(time(doc, "updatedAt"));
        purchaseRepository.save(purchase);

        purchaseItemRepository.deleteByPurchaseId(id);
        Object items = doc.get("items");
        if (items instanceof List<?> list) {
            for (Object itemObj : list) {
                if (!(itemObj instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) itemMap;
                purchaseItemRepository.save(PurchaseItem.builder()
                        .id(Ids.uuid())
                        .purchaseId(id)
                        .productId(nul(item, "productId"))
                        .name(str(item, "name"))
                        .qty(dbl(item, "qty"))
                        .unitCost(dbl(item, "unitCost"))
                        .batchNumber(nul(item, "batchNumber"))
                        .expiryDate(item.get("expiryDate") == null ? null
                                : java.time.LocalDate.parse(String.valueOf(item.get("expiryDate"))))
                        .build());
            }
        }
    }

    private void upsertSupplier(String shopId, Map<String, Object> doc) {
        String id = str(doc, "supplierId");
        Supplier supplier = supplierRepository.findById(id).orElseGet(() -> Supplier.builder()
                .supplierId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(supplier.getShopId()) && supplier.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        supplier.setShopId(shopId);
        supplier.setName(str(doc, "name"));
        supplier.setPhone(nul(doc, "phone"));
        supplier.setDeleted(bool(doc, "isDeleted"));
        supplier.setUpdatedAt(time(doc, "updatedAt"));
        supplierRepository.save(supplier);
    }

    private void upsertExpense(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "expenseId");
        Expense expense = expenseRepository.findById(id).orElseGet(() -> Expense.builder()
                .expenseId(id).shopId(shopId).branchId(user.getBranchId()).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(expense.getShopId()) && expense.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        if (expense.getBranchId() != null && !user.getBranchId().equals(expense.getBranchId())) {
            throw BusinessException.forbidden("Document belongs to another branch");
        }
        expense.setShopId(shopId);
        expense.setBranchId(user.getBranchId());
        if (nul(doc, "staffId") != null) {
            expense.setStaffId(nul(doc, "staffId"));
        }
        expense.setDescription(str(doc, "description"));
        expense.setAmount(dbl(doc, "amount"));
        expense.setCategoryId(nul(doc, "categoryId"));
        expense.setCategoryLabel(nul(doc, "categoryLabel"));
        expense.setDate(time(doc, "date"));
        expense.setNotes(nul(doc, "notes"));
        expense.setDeleted(bool(doc, "isDeleted"));
        expense.setUpdatedAt(time(doc, "updatedAt"));
        expenseRepository.save(expense);
    }

    private void upsertSupplierReturn(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "returnId");
        if (id.isBlank()) {
            throw BusinessException.badRequest("Supplier return ID is required");
        }
        var existingReturn = supplierReturnRepository.findById(id);
        if (existingReturn.isPresent()) {
            ensureShop(shopId, existingReturn.get().getShopId());
            if (existingReturn.get().getBranchId() != null
                    && !user.getBranchId().equals(existingReturn.get().getBranchId())) {
                throw BusinessException.forbidden("Document belongs to another branch");
            }
            // Sync retries must be idempotent. The local stock mutation was
            // already applied when this event was first accepted.
            if (!existingReturn.get().isDeleted() && !bool(doc, "isDeleted")) return;
        }
        SupplierReturn item = supplierReturnRepository.findById(id).orElseGet(() -> SupplierReturn.builder()
                .returnId(id).shopId(shopId).branchId(user.getBranchId()).createdAt(time(doc, "createdAt"))
                .updatedAt(LocalDateTime.now()).itemsJson("[]").build());
        ensureShop(shopId, item.getShopId());
        if (item.getBranchId() != null && !user.getBranchId().equals(item.getBranchId())) {
            throw BusinessException.forbidden("Document belongs to another branch");
        }
        String orderId = str(doc, "orderId");
        if (orderId.isBlank()) {
            throw BusinessException.badRequest("Purchase order is required for a supplier return");
        }
        String reason = str(doc, "reason").trim();
        if (reason.length() < 3) {
            throw BusinessException.badRequest("Supplier return reason is required");
        }
        PurchaseOrder order = purchaseOrderRepository
                .findByOrderIdAndShopIdAndBranchId(orderId, shopId, user.getBranchId())
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> BusinessException.notFound("Purchase order not found for this branch"));
        Map<String, Double> receivedByProduct = readQuantities(order.getReceivedQtyJson());
        Map<String, Double> alreadyReturnedByProduct = new HashMap<>();
        for (SupplierReturn previous : supplierReturnRepository
                .findByShopIdAndBranchIdAndOrderIdAndIsDeletedFalse(
                        shopId, user.getBranchId(), orderId)) {
            if (!orderId.equals(previous.getOrderId()) || previous.getReturnId().equals(id)) continue;
            Object previousItems = readJson(previous.getItemsJson());
            if (!(previousItems instanceof List<?> previousLines)) continue;
            for (Object raw : previousLines) {
                if (!(raw instanceof Map<?, ?> rawMap)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> line = (Map<String, Object>) rawMap;
                String productId = str(line, "productId");
                alreadyReturnedByProduct.merge(productId, dbl(line, "qty"), Double::sum);
            }
        }
        Map<String, PurchaseOrderItem> orderItems = new HashMap<>();
        for (PurchaseOrderItem orderItem : purchaseOrderItemRepository.findByOrderId(orderId)) {
            orderItems.put(orderItem.getProductId(), orderItem);
        }
        Object rawItems = doc.get("items");
        if (!(rawItems instanceof List<?> lines) || lines.isEmpty()) {
            throw BusinessException.badRequest("Supplier return must contain at least one item");
        }
        double calculatedTotal = 0;
        for (Object rawLine : lines) {
            if (!(rawLine instanceof Map<?, ?> rawMap)) {
                throw BusinessException.badRequest("Invalid supplier return item");
            }
            @SuppressWarnings("unchecked") Map<String, Object> line = (Map<String, Object>) rawMap;
            String productId = str(line, "productId");
            double quantity = dbl(line, "qty");
            double unitCost = dbl(line, "unitCost");
            if (productId.isBlank() || !Double.isFinite(quantity) || quantity <= 0
                    || quantity != Math.floor(quantity)) {
                throw BusinessException.badRequest("Supplier return quantity must be a positive integer");
            }
            if (!Double.isFinite(unitCost) || unitCost < 0) {
                throw BusinessException.badRequest("Supplier return unit cost is invalid");
            }
            if (productRepository.findByProductIdAndShopIdAndBranchIdAndIsDeletedFalse(
                    productId, shopId, user.getBranchId()).isEmpty()) {
                throw BusinessException.notFound("Supplier return product not found for this branch");
            }
            PurchaseOrderItem orderItem = orderItems.get(productId);
            double received = receivedByProduct.getOrDefault(productId, 0d);
            double alreadyReturned = alreadyReturnedByProduct.getOrDefault(productId, 0d);
            if (orderItem == null || received <= 0 || alreadyReturned + quantity > received + 0.0001) {
                throw BusinessException.badRequest("Supplier return exceeds the received quantity for " + productId);
            }
            calculatedTotal += quantity * unitCost;
        }
        double suppliedTotal = dbl(doc, "totalAmount");
        if (!Double.isFinite(suppliedTotal) || suppliedTotal < 0
                || Math.abs(suppliedTotal - calculatedTotal) > 0.01) {
            throw BusinessException.badRequest("Supplier return total does not match the item lines");
        }
        item.setShopId(shopId);
        item.setBranchId(user.getBranchId());
        item.setOrderId(orderId);
        item.setSupplierId(nul(doc, "supplierId"));
        item.setItemsJson(writeJson(doc.get("items")));
        item.setTotalAmount(Math.round(calculatedTotal * 100.0) / 100.0);
        item.setReason(reason);
        String actorId = nul(doc, "actorId");
        if (item.getActorId() == null && actorId != null
                && !user.getUserId().equals(actorId)) {
            throw BusinessException.forbidden("Return actor does not match the signed-in user");
        }
        if (item.getActorId() == null) item.setActorId(
                actorId == null ? user.getUserId() : actorId);
        item.setCreatedAt(time(doc, "createdAt"));
        item.setUpdatedAt(time(doc, "updatedAt"));
        item.setDeleted(bool(doc, "isDeleted"));
        supplierReturnRepository.save(item);
    }

    private Map<String, Double> readQuantities(String value) {
        Object parsed = readJson(value);
        Map<String, Double> result = new HashMap<>();
        if (parsed instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                try {
                    result.put(String.valueOf(entry.getKey()), Double.parseDouble(String.valueOf(entry.getValue())));
                } catch (NumberFormatException ignored) { }
            }
        }
        return result;
    }

    private Object readJson(String value) {
        try { return mapper.readValue(value == null ? "{}" : value, Object.class); }
        catch (Exception ignored) { return Map.of(); }
    }

    /** Sync writes are already applied to local stock; this method only
     * persists the authoritative return event and its line items. */
    private void upsertSaleReturn(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "returnId");
        String payloadBranchId = nul(doc, "branchId");
        if (payloadBranchId != null && !user.getBranchId().equals(payloadBranchId)) {
            throw BusinessException.forbidden("Sale return belongs to another branch");
        }
        SaleReturn item = saleReturnRepository.findById(id).orElseGet(() -> SaleReturn.builder()
                .returnId(id).shopId(shopId).branchId(user.getBranchId())
                .createdAt(time(doc, "createdAt")).updatedAt(LocalDateTime.now()).build());
        ensureShop(shopId, item.getShopId());
        if (item.getBranchId() != null && !user.getBranchId().equals(item.getBranchId())) {
            throw BusinessException.forbidden("Sale return belongs to another branch");
        }
        item.setShopId(shopId);
        item.setBranchId(user.getBranchId());
        item.setSaleId(str(doc, "saleId"));
        item.setCustomerId(str(doc, "customerId"));
        item.setRefundAmount(dbl(doc, "refundAmount"));
        item.setRefundMethod(str(doc, "refundMethod"));
        item.setReason(nul(doc, "reason"));
        item.setStaffId(nul(doc, "staffId"));
        item.setCreatedAt(time(doc, "createdAt"));
        item.setUpdatedAt(time(doc, "updatedAt"));
        item.setDeleted(bool(doc, "isDeleted"));
        saleReturnRepository.save(item);
        saleReturnItemRepository.deleteByReturnId(id);
        Object rawItems = doc.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object raw : list) {
                if (!(raw instanceof Map<?, ?> rawMap)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> line = (Map<String, Object>) rawMap;
                saleReturnItemRepository.save(SaleReturnItem.builder()
                        .returnItemId(str(line, "returnItemId").isBlank() ? Ids.uuid() : str(line, "returnItemId"))
                        .returnId(id)
                        .saleItemId(str(line, "saleItemId"))
                        .productId(nul(line, "productId"))
                        .name(str(line, "name"))
                        .qty(dbl(line, "qty"))
                        .unitPrice(dbl(line, "unitPrice"))
                        .purchasePrice(dbl(line, "purchasePrice"))
                        .build());
            }
        }
    }

    private String writeJson(Object value) {
        try { return mapper.writeValueAsString(value == null ? List.of() : value); }
        catch (JsonProcessingException e) { throw BusinessException.badRequest("Invalid supplier return items"); }
    }

    private void upsertVendor(String shopId, Map<String, Object> doc) {
        String id = str(doc, "vendorId");
        Vendor vendor = vendorRepository.findById(id).orElseGet(() -> Vendor.builder()
                .vendorId(id).shopId(shopId).createdAt(time(doc, "createdAt"))
                .updatedAt(LocalDateTime.now()).build());
        ensureShop(shopId, vendor.getShopId());
        vendor.setShopId(shopId);
        vendor.setCompanyName(str(doc, "companyName"));
        vendor.setContactPerson(nul(doc, "contactPerson"));
        vendor.setPhone(nul(doc, "phone"));
        vendor.setEmail(nul(doc, "email"));
        vendor.setCategory(nul(doc, "category"));
        vendor.setAddress(nul(doc, "address"));
        vendor.setProducts(nul(doc, "products"));
        vendor.setRating(dbl(doc, "rating"));
        vendor.setTotalOrders((int) dbl(doc, "totalOrders"));
        vendor.setTotalSpent(dbl(doc, "totalSpent"));
        vendor.setPendingAmount(dbl(doc, "pendingAmount"));
        vendor.setLastPurchaseDate(nullableTime(doc, "lastPurchaseDate"));
        vendor.setStatus(str(doc, "status"));
        vendor.setDeleted(bool(doc, "isDeleted"));
        vendor.setCreatedAt(time(doc, "createdAt"));
        vendor.setUpdatedAt(time(doc, "updatedAt"));
        vendorRepository.save(vendor);
    }

    private void upsertVendorPurchase(String shopId, Map<String, Object> doc) {
        String id = str(doc, "purchaseId");
        VendorPurchase purchase = vendorPurchaseRepository.findById(id).orElseGet(() ->
                VendorPurchase.builder().purchaseId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        ensureShop(shopId, purchase.getShopId());
        purchase.setShopId(shopId);
        purchase.setVendorId(str(doc, "vendorId"));
        purchase.setAmount(dbl(doc, "amount"));
        purchase.setDate(time(doc, "date"));
        purchase.setStatus(str(doc, "status"));
        purchase.setNotes(nul(doc, "notes"));
        purchase.setItemsSupplied(nul(doc, "itemsSupplied"));
        purchase.setDeleted(bool(doc, "isDeleted"));
        purchase.setUpdatedAt(time(doc, "updatedAt"));
        vendorPurchaseRepository.save(purchase);
    }

    private void upsertVendorPayment(String shopId, Map<String, Object> doc) {
        String id = str(doc, "paymentId");
        VendorPayment payment = vendorPaymentRepository.findById(id).orElseGet(() ->
                VendorPayment.builder().paymentId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        ensureShop(shopId, payment.getShopId());
        payment.setShopId(shopId);
        payment.setVendorId(str(doc, "vendorId"));
        payment.setAmount(dbl(doc, "amount"));
        payment.setDate(time(doc, "date"));
        payment.setMethod(nul(doc, "method"));
        payment.setReference(nul(doc, "reference"));
        payment.setNotes(nul(doc, "notes"));
        payment.setDeleted(bool(doc, "isDeleted"));
        payment.setUpdatedAt(time(doc, "updatedAt"));
        vendorPaymentRepository.save(payment);
    }

    private void ensureShop(String expectedShopId, String actualShopId) {
        if (actualShopId != null && !expectedShopId.equals(actualShopId)) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
    }

    private void upsertShopUser(CurrentUser user, Map<String, Object> doc) {
        // users collection: only staff of this shop; owner is derived from the token
        String userId = str(doc, "userId");
        if (userId.equals(user.getUserId())) {
            log.debug("SYNC_PUSH skip self user document");
        } else {
            log.debug("SYNC_PUSH accepted user doc userId={} shopId={}", userId, user.getShopId());
        }
    }

    // -------- json out --------

    private Map<String, Object> productJson(Product p) {
        Map<String, Object> json = new HashMap<>();
        json.put("productId", p.getProductId());
        json.put("nameEn", p.getNameEn());
        json.put("nameBn", p.getNameBn());
        json.put("barcode", p.getBarcode());
        json.put("sku", p.getSku());
        json.put("variantName", p.getVariantName());
        json.put("batchNumber", p.getBatchNumber());
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
        json.put("updatedAt", p.getUpdatedAt().format(ISO));
        json.put("isDeleted", p.isDeleted());
        return json;
    }

    private LocalDate expiryFrom(Map<String, Object> doc) {
        Object value = doc.get("expiryDate");
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
        } catch (Exception e) {
            log.warn("SYNC_PRODUCT_EXPIRY_PARSE_FAILED value={}", raw);
            return null;
        }
    }

    private Map<String, Object> customerJson(Customer c) {
        Map<String, Object> json = new HashMap<>();
        json.put("customerId", c.getCustomerId());
        json.put("name", c.getName());
        json.put("phone", c.getPhone());
        json.put("email", c.getEmail());
        json.put("address", c.getAddress());
        json.put("notes", c.getNotes());
        json.put("imagePath", c.getImagePath());
        json.put("totalSpent", c.getTotalSpent());
        json.put("tag", c.getTag());
        json.put("creditLimit", c.getCreditLimit());
        json.put("loyaltyPoints", c.getLoyaltyPoints());
        json.put("shopId", c.getShopId());
        json.put("updatedAt", c.getUpdatedAt().format(ISO));
        json.put("isDeleted", c.isDeleted());
        return json;
    }

    private Map<String, Object> saleJson(Sale s) {
        List<Map<String, Object>> items = saleItemRepository.findBySaleId(s.getSaleId()).stream().map(i -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", i.getProductId());
            m.put("saleItemId", i.getId());
            m.put("name", i.getName());
            m.put("qty", i.getQty());
            m.put("unitPrice", i.getUnitPrice());
            m.put("purchasePrice", i.getPurchasePrice());
            m.put("batchAllocations", saleItemBatchAllocationRepository
                    .findBySaleItemId(i.getId()).stream().map(a -> Map.of(
                            "batchId", a.getBatchId(),
                            "quantity", a.getQuantity())).toList());
            return m;
        }).toList();
        Map<String, Object> json = new HashMap<>();
        json.put("saleId", s.getSaleId());
        json.put("invoiceNumber", s.getInvoiceNumber());
        json.put("customerId", s.getCustomerId());
        json.put("items", items);
        json.put("discount", s.getDiscount());
        json.put("vatPercent", s.getVatPercent());
        json.put("charge", s.getCharge());
        json.put("totalAmount", s.getTotalAmount());
        json.put("paidAmount", s.getPaidAmount());
        json.put("dueAmount", s.getDueAmount());
        json.put("paymentStatus", s.getPaymentStatus());
        json.put("paymentMethod", s.getPaymentMethod());
        json.put("loyaltyPointsRedeemed", s.getLoyaltyPointsRedeemed());
        json.put("loyaltyPointsEarned", s.getLoyaltyPointsEarned());
        json.put("staffId", s.getStaffId());
        json.put("branchId", s.getBranchId());
        json.put("discountStaffId", s.getDiscountStaffId());
        json.put("voidedByStaffId", s.getVoidedByStaffId());
        json.put("voidedAt", s.getVoidedAt() == null ? null : s.getVoidedAt().format(ISO));
        json.put("shopId", s.getShopId());
        json.put("createdAt", s.getCreatedAt().format(ISO));
        json.put("updatedAt", s.getUpdatedAt().format(ISO));
        json.put("isDeleted", s.isDeleted());
        return json;
    }

    private Map<String, Object> ledgerJson(CustomerLedgerEntry e) {
        Map<String, Object> json = new HashMap<>();
        json.put("entryId", e.getEntryId());
        json.put("customerId", e.getCustomerId());
        json.put("type", e.getType());
        json.put("amount", e.getAmount());
        json.put("note", e.getNote());
        json.put("shopId", e.getShopId());
        json.put("createdAt", e.getCreatedAt().format(ISO));
        json.put("updatedAt", e.getUpdatedAt().format(ISO));
        json.put("isDeleted", e.isDeleted());
        return json;
    }

    private Map<String, Object> paymentJson(Payment p) {
        Map<String, Object> json = new HashMap<>();
        json.put("paymentId", p.getPaymentId());
        json.put("customerId", p.getCustomerId());
        json.put("saleId", p.getSaleId());
        json.put("amount", p.getAmount());
        json.put("paymentMethod", p.getPaymentMethod());
        json.put("reference", p.getReference());
        json.put("staffId", p.getStaffId());
        json.put("branchId", p.getBranchId());
        json.put("shopId", p.getShopId());
        json.put("createdAt", p.getCreatedAt().format(ISO));
        json.put("updatedAt", p.getUpdatedAt().format(ISO));
        json.put("isDeleted", p.isDeleted());
        return json;
    }

    private Map<String, Object> purchaseJson(Purchase p) {
        List<Map<String, Object>> items = purchaseItemRepository.findByPurchaseId(p.getPurchaseId()).stream().map(i -> {
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

    private Map<String, Object> vendorJson(Vendor v) {
        Map<String, Object> json = new HashMap<>();
        json.put("vendorId", v.getVendorId());
        json.put("companyName", v.getCompanyName());
        json.put("contactPerson", v.getContactPerson());
        json.put("phone", v.getPhone());
        json.put("email", v.getEmail());
        json.put("category", v.getCategory());
        json.put("address", v.getAddress());
        json.put("products", v.getProducts());
        json.put("rating", v.getRating());
        json.put("totalOrders", v.getTotalOrders());
        json.put("totalSpent", v.getTotalSpent());
        json.put("pendingAmount", v.getPendingAmount());
        json.put("lastPurchaseDate", v.getLastPurchaseDate() == null ? null : v.getLastPurchaseDate().format(ISO));
        json.put("status", v.getStatus());
        json.put("shopId", v.getShopId());
        json.put("createdAt", v.getCreatedAt().format(ISO));
        json.put("updatedAt", v.getUpdatedAt().format(ISO));
        json.put("isDeleted", v.isDeleted());
        return json;
    }

    private Map<String, Object> vendorPurchaseJson(VendorPurchase p) {
        Map<String, Object> json = new HashMap<>();
        json.put("purchaseId", p.getPurchaseId());
        json.put("vendorId", p.getVendorId());
        json.put("amount", p.getAmount());
        json.put("date", p.getDate().format(ISO));
        json.put("status", p.getStatus());
        json.put("notes", p.getNotes());
        json.put("itemsSupplied", p.getItemsSupplied());
        json.put("shopId", p.getShopId());
        json.put("updatedAt", p.getUpdatedAt().format(ISO));
        json.put("isDeleted", p.isDeleted());
        return json;
    }

    private Map<String, Object> vendorPaymentJson(VendorPayment p) {
        Map<String, Object> json = new HashMap<>();
        json.put("paymentId", p.getPaymentId());
        json.put("vendorId", p.getVendorId());
        json.put("amount", p.getAmount());
        json.put("date", p.getDate().format(ISO));
        json.put("method", p.getMethod());
        json.put("reference", p.getReference());
        json.put("notes", p.getNotes());
        json.put("shopId", p.getShopId());
        json.put("updatedAt", p.getUpdatedAt().format(ISO));
        json.put("isDeleted", p.isDeleted());
        return json;
    }

    private Map<String, Object> expenseJson(Expense e) {
        Map<String, Object> json = new HashMap<>();
        json.put("expenseId", e.getExpenseId());
        json.put("description", e.getDescription());
        json.put("amount", e.getAmount());
        json.put("categoryId", e.getCategoryId());
        json.put("categoryLabel", e.getCategoryLabel());
        json.put("date", e.getDate().format(ISO));
        json.put("notes", e.getNotes());
        json.put("shopId", e.getShopId());
        json.put("branchId", e.getBranchId());
        json.put("staffId", e.getStaffId());
        json.put("updatedAt", e.getUpdatedAt().format(ISO));
        json.put("isDeleted", e.isDeleted());
        return json;
    }

    private Map<String, Object> supplierReturnJson(SupplierReturn r) {
        Map<String, Object> json = new HashMap<>();
        json.put("returnId", r.getReturnId());
        json.put("orderId", r.getOrderId());
        json.put("supplierId", r.getSupplierId());
        try { json.put("items", mapper.readValue(r.getItemsJson(), List.class)); }
        catch (Exception e) { json.put("items", List.of()); }
        json.put("totalAmount", r.getTotalAmount());
        json.put("reason", r.getReason());
        json.put("actorId", r.getActorId());
        json.put("shopId", r.getShopId());
        json.put("branchId", r.getBranchId());
        json.put("createdAt", r.getCreatedAt().format(ISO));
        json.put("updatedAt", r.getUpdatedAt().format(ISO));
        json.put("isDeleted", r.isDeleted());
        return json;
    }

    private Map<String, Object> saleReturnJson(SaleReturn r) {
        Map<String, Object> json = new HashMap<>();
        json.put("returnId", r.getReturnId());
        json.put("shopId", r.getShopId());
        json.put("branchId", r.getBranchId());
        json.put("saleId", r.getSaleId());
        json.put("customerId", r.getCustomerId());
        json.put("refundAmount", r.getRefundAmount());
        json.put("refundMethod", r.getRefundMethod());
        json.put("reason", r.getReason());
        json.put("staffId", r.getStaffId());
        json.put("items", saleReturnItemRepository.findByReturnId(r.getReturnId()).stream().map(item -> Map.of(
                "returnItemId", item.getReturnItemId(), "saleItemId", item.getSaleItemId(),
                "productId", item.getProductId() == null ? "" : item.getProductId(),
                "name", item.getName(), "qty", item.getQty(), "unitPrice", item.getUnitPrice(),
                "purchasePrice", item.getPurchasePrice())).toList());
        json.put("createdAt", r.getCreatedAt().format(ISO));
        json.put("updatedAt", r.getUpdatedAt().format(ISO));
        json.put("isDeleted", r.isDeleted());
        return json;
    }

    private void upsertStockAdjustment(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "adjustmentId");
        StockAdjustment item = stockAdjustmentRepository.findById(id).orElseGet(() -> StockAdjustment.builder()
                .adjustmentId(id).shopId(shopId).branchId(user.getBranchId()).createdAt(time(doc, "createdAt"))
                .updatedAt(LocalDateTime.now()).build());
        ensureShop(shopId, item.getShopId());
        String payloadBranchId = nul(doc, "branchId");
        if (payloadBranchId != null && !user.getBranchId().equals(payloadBranchId)) {
            throw BusinessException.forbidden("Document belongs to another branch");
        }
        if (payloadBranchId == null && item.getBranchId() != null
                && !user.getBranchId().equals(item.getBranchId())) {
            throw BusinessException.forbidden("Document belongs to another branch");
        }
        String productId = str(doc, "productId");
        if (productId.isBlank()) {
            throw BusinessException.badRequest("Stock adjustment product is required");
        }
        if (productRepository.findByProductIdAndShopIdAndBranchIdAndIsDeletedFalse(
                productId, shopId, user.getBranchId()).isEmpty()) {
            throw BusinessException.notFound("Product not found for this branch");
        }
        double rawDelta = dbl(doc, "delta");
        if (!Double.isFinite(rawDelta) || rawDelta == 0 || rawDelta != Math.floor(rawDelta)) {
            throw BusinessException.badRequest("Stock adjustment delta must be a non-zero integer");
        }
        String reason = str(doc, "reason").trim();
        if (reason.length() < 3) {
            throw BusinessException.badRequest("Stock adjustment reason is required");
        }
        String payloadActorId = nul(doc, "actorId");
        if (item.getActorId() == null && payloadActorId != null
                && !user.getUserId().equals(payloadActorId)) {
            throw BusinessException.forbidden("Adjustment actor does not match the signed-in user");
        }
        String status = str(doc, "status").toLowerCase();
        if (!List.of("pendingapproval", "approved", "rejected").contains(status)) {
            throw BusinessException.badRequest("Invalid stock adjustment status");
        }
        if (("approved".equals(status) || "rejected".equals(status))
                && (user.getRole() == null || !(user.getRole().equalsIgnoreCase("OWNER")
                || user.getRole().equalsIgnoreCase("ADMIN")))) {
            throw BusinessException.forbidden("Only an owner or admin can decide a stock adjustment");
        }
        if ("approved".equals(status) || "rejected".equals(status)) {
            String approverId = nul(doc, "approverId");
            if (approverId == null || !user.getUserId().equals(approverId)) {
                throw BusinessException.forbidden("Approval actor does not match the signed-in user");
            }
        }
        item.setShopId(shopId);
        item.setBranchId(payloadBranchId == null
                ? (item.getBranchId() == null ? user.getBranchId() : item.getBranchId())
                : payloadBranchId);
        item.setProductId(productId);
        item.setDelta((int) rawDelta);
        item.setReason(reason);
        if (item.getActorId() == null) item.setActorId(
                payloadActorId == null ? user.getUserId() : payloadActorId);
        item.setApproverId(nul(doc, "approverId"));
        item.setStatus(status);
        item.setCreatedAt(time(doc, "createdAt"));
        item.setUpdatedAt(time(doc, "updatedAt"));
        item.setDeleted(bool(doc, "isDeleted"));
        stockAdjustmentRepository.save(item);
    }

    private Map<String, Object> stockAdjustmentJson(StockAdjustment a) {
        Map<String, Object> json = new HashMap<>();
        json.put("adjustmentId", a.getAdjustmentId());
        json.put("productId", a.getProductId());
        json.put("delta", a.getDelta());
        json.put("reason", a.getReason());
        json.put("actorId", a.getActorId());
        json.put("approverId", a.getApproverId());
        json.put("status", a.getStatus());
        json.put("shopId", a.getShopId());
        json.put("branchId", a.getBranchId());
        json.put("createdAt", a.getCreatedAt().format(ISO));
        json.put("updatedAt", a.getUpdatedAt().format(ISO));
        json.put("isDeleted", a.isDeleted());
        return json;
    }

    private void upsertAuditEvent(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "eventId");
        GuardianAuditEvent event = guardianAuditEventRepository.findById(id).orElseGet(() ->
                GuardianAuditEvent.builder().eventId(id).shopId(shopId).createdAt(time(doc, "createdAt")).build());
        ensureShop(shopId, event.getShopId());
        String payloadBranchId = nul(doc, "branchId");
        if (payloadBranchId != null && !user.getBranchId().equals(payloadBranchId)) {
            throw BusinessException.forbidden("Audit event belongs to another branch");
        }
        event.setShopId(shopId);
        event.setBranchId(user.getBranchId());
        event.setActorUserId(str(doc, "actorId"));
        event.setAction(str(doc, "action"));
        event.setEntityType(str(doc, "entityType"));
        event.setEntityId(nul(doc, "entityId"));
        event.setMetadata(writeJson(doc.get("details")));
        event.setCreatedAt(time(doc, "createdAt"));
        guardianAuditEventRepository.save(event);
    }

    private void upsertProductBatch(CurrentUser user, Map<String, Object> doc) {
        String shopId = user.getShopId();
        String id = str(doc, "batchId");
        String payloadBranchId = nul(doc, "branchId");
        if (payloadBranchId != null && !user.getBranchId().equals(payloadBranchId)) {
            throw BusinessException.forbidden("Product batch belongs to another branch");
        }
        ProductBatch batch = productBatchRepository.findById(id).orElseGet(() -> ProductBatch.builder()
                .batchId(id).shopId(shopId).branchId(user.getBranchId()).updatedAt(LocalDateTime.now()).build());
        ensureShop(shopId, batch.getShopId());
        if (batch.getBranchId() != null && !user.getBranchId().equals(batch.getBranchId())) {
            throw BusinessException.forbidden("Product batch belongs to another branch");
        }
        batch.setShopId(shopId);
        batch.setBranchId(user.getBranchId());
        batch.setProductId(str(doc, "productId"));
        batch.setBatchNumber(str(doc, "batchNumber"));
        batch.setExpiryDate(doc.get("expiryDate") == null ? null : LocalDate.parse(String.valueOf(doc.get("expiryDate"))));
        batch.setQuantity((int) dbl(doc, "quantity"));
        batch.setUnitCost(dbl(doc, "unitCost"));
        batch.setUpdatedAt(time(doc, "updatedAt"));
        batch.setDeleted(bool(doc, "isDeleted"));
        productBatchRepository.save(batch);
    }

    private Map<String, Object> productBatchJson(ProductBatch b) {
        Map<String, Object> json = new HashMap<>();
        json.put("batchId", b.getBatchId());
        json.put("productId", b.getProductId());
        json.put("batchNumber", b.getBatchNumber());
        json.put("expiryDate", b.getExpiryDate());
        json.put("quantity", b.getQuantity());
        json.put("unitCost", b.getUnitCost());
        json.put("shopId", b.getShopId());
        json.put("branchId", b.getBranchId());
        json.put("updatedAt", b.getUpdatedAt().format(ISO));
        json.put("isDeleted", b.isDeleted());
        return json;
    }

    private Map<String, Object> auditEventJson(GuardianAuditEvent e) {
        Map<String, Object> json = new HashMap<>();
        json.put("eventId", e.getEventId());
        json.put("action", e.getAction());
        json.put("entityType", e.getEntityType());
        json.put("entityId", e.getEntityId());
        json.put("actorId", e.getActorUserId());
        json.put("shopId", e.getShopId());
        json.put("branchId", e.getBranchId());
        try { json.put("details", mapper.readValue(e.getMetadata() == null ? "{}" : e.getMetadata(), Map.class)); }
        catch (Exception ignored) { json.put("details", Map.of()); }
        json.put("createdAt", e.getCreatedAt().format(ISO));
        json.put("updatedAt", e.getCreatedAt().format(ISO));
        json.put("isDeleted", false);
        return json;
    }

    private Map<String, Object> shopUserJson(CurrentUser user) {
        Map<String, Object> json = new HashMap<>();
        json.put("userId", user.getUserId());
        json.put("name", user.getName() == null ? "" : user.getName());
        json.put("phone", "");
        json.put("shopId", user.getShopId());
        json.put("role", user.getRole() == null ? "OWNER" : user.getRole());
        json.put("updatedAt", LocalDateTime.now().format(ISO));
        json.put("syncStatus", "synced");
        return json;
    }

    // -------- helpers --------

    private String idKeyFor(String collection) {
        return switch (collection) {
            case "products" -> "productId";
            case "customers" -> "customerId";
            case "sales" -> "saleId";
            case "customerLedgerEntries" -> "entryId";
            case "payments" -> "paymentId";
            case "purchases" -> "purchaseId";
            case "suppliers" -> "supplierId";
            case "vendors" -> "vendorId";
            case "vendorPurchases" -> "purchaseId";
            case "vendorPayments" -> "paymentId";
            case "expenses" -> "expenseId";
            case "supplierReturns" -> "returnId";
            case "saleReturns" -> "returnId";
            case "stockAdjustments" -> "adjustmentId";
            case "auditEvents" -> "eventId";
            case "productBatches" -> "batchId";
            case "users" -> "userId";
            default -> "id";
        };
    }

    private String str(Map<String, Object> doc, String key) {
        Object v = doc.get(key);
        return v == null ? "" : v.toString();
    }

    private String nul(Map<String, Object> doc, String key) {
        Object v = doc.get(key);
        return v == null ? null : v.toString();
    }

    private double dbl(Map<String, Object> doc, String key) {
        Object v = doc.get(key);
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(v.toString());
    }

    private int intValue(Map<String, Object> doc, String key) {
        Object v = doc.get(key);
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) { return 0; }
    }

    private boolean bool(Map<String, Object> doc, String key) {
        Object v = doc.get(key);
        return v instanceof Boolean b && b;
    }

    private LocalDateTime time(Map<String, Object> doc, String key) {
        Object v = doc.get(key);
        if (v == null) {
            return LocalDateTime.now();
        }
        return TimeUtil.parse(String.valueOf(v), LocalDateTime.now());
    }

    private LocalDateTime nullableTime(Map<String, Object> doc, String key) {
        Object v = doc.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : TimeUtil.parse(String.valueOf(v), null);
    }
}
