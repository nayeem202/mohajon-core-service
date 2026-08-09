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
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.purchase.domain.Purchase;
import com.haalkhata.feature.purchase.domain.PurchaseItem;
import com.haalkhata.feature.purchase.domain.Supplier;
import com.haalkhata.feature.purchase.infrastructure.PurchaseItemRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseRepository;
import com.haalkhata.feature.purchase.infrastructure.SupplierRepository;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.domain.SaleItem;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
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
public class SyncService {

    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final List<String> COLLECTIONS = List.of(
            "products", "customers", "sales", "customerLedgerEntries",
            "payments", "purchases", "suppliers", "expenses", "users");

    private final ObjectMapper mapper;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CustomerLedgerEntryRepository ledgerRepository;
    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseRepository expenseRepository;

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
                case "products" -> upsertProduct(user.getShopId(), doc);
                case "customers" -> upsertCustomer(user.getShopId(), doc);
                case "sales" -> upsertSale(user.getShopId(), doc);
                case "customerLedgerEntries" -> upsertLedgerEntry(user.getShopId(), doc);
                case "payments" -> upsertPayment(user.getShopId(), doc);
                case "purchases" -> upsertPurchase(user.getShopId(), doc);
                case "suppliers" -> upsertSupplier(user.getShopId(), doc);
                case "expenses" -> upsertExpense(user.getShopId(), doc);
                case "users" -> upsertShopUser(user, doc);
            }
            saved++;
        }
        log.info("SYNC_PUSH collection={} shopId={} saved={}", collection, user.getShopId(), saved);
        return saved;
    }

    public List<Map<String, Object>> pull(CurrentUser user, String collection, LocalDateTime after) {
        if (!COLLECTIONS.contains(collection)) {
            throw BusinessException.badRequest("Unknown collection: " + collection);
        }
        List<Map<String, Object>> docs = switch (collection) {
            case "products" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                productRepository.findByShopIdAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(p -> d.add(productJson(p)));
                productRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(p -> d.add(productJson(p)));
                yield d;
            }
            case "customers" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                customerRepository.findByShopIdAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(c -> d.add(customerJson(c)));
                customerRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(c -> d.add(customerJson(c)));
                yield d;
            }
            case "sales" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                saleRepository.findByShopIdAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(s -> d.add(saleJson(s)));
                saleRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(s -> d.add(saleJson(s)));
                yield d;
            }
            case "customerLedgerEntries" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                ledgerRepository.findByShopIdAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(e -> d.add(ledgerJson(e)));
                ledgerRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(e -> d.add(ledgerJson(e)));
                yield d;
            }
            case "payments" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                paymentRepository.findByShopIdAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(p -> d.add(paymentJson(p)));
                paymentRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(p -> d.add(paymentJson(p)));
                yield d;
            }
            case "purchases" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                purchaseRepository.findByShopIdAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(p -> d.add(purchaseJson(p)));
                purchaseRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(p -> d.add(purchaseJson(p)));
                yield d;
            }
            case "suppliers" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                supplierRepository.findByShopIdAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(s -> d.add(supplierJson(s)));
                supplierRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(s -> d.add(supplierJson(s)));
                yield d;
            }
            case "expenses" -> {
                List<Map<String, Object>> d = new ArrayList<>();
                expenseRepository.findByShopIdAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(e -> d.add(expenseJson(e)));
                expenseRepository.findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(user.getShopId(), after)
                        .forEach(e -> d.add(expenseJson(e)));
                yield d;
            }
            case "users" -> List.of(shopUserJson(user));
            default -> List.of();
        };
        log.info("SYNC_PULL collection={} shopId={} after={} docs={}",
                collection, user.getShopId(), after, docs.size());
        return docs;
    }

    // -------- upserts --------

    private void upsertProduct(String shopId, Map<String, Object> doc) {
        String id = str(doc, "productId");
        Product product = productRepository.findById(id).orElseGet(() -> Product.builder()
                .productId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(product.getShopId()) && product.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        product.setShopId(shopId);
        product.setNameEn(str(doc, "nameEn"));
        product.setNameBn(nul(doc, "nameBn"));
        product.setBarcode(nul(doc, "barcode"));
        product.setCategory(nul(doc, "category"));
        product.setUnit(nul(doc, "unit"));
        product.setImagePath(nul(doc, "imagePath"));
        product.setBuyingPrice(dbl(doc, "buyingPrice"));
        product.setPurchasePrice(dbl(doc, "purchasePrice"));
        product.setSellingPrice(dbl(doc, "sellingPrice"));
        product.setStockQty(doc.get("stockQty") == null ? 0 : ((Number) doc.get("stockQty")).intValue());
        product.setLowStockAlert(doc.get("lowStockAlert") == null ? 0 : ((Number) doc.get("lowStockAlert")).intValue());
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
        customer.setDeleted(bool(doc, "isDeleted"));
        customer.setUpdatedAt(time(doc, "updatedAt"));
        customerRepository.save(customer);
    }

    private void upsertSale(String shopId, Map<String, Object> doc) {
        String id = str(doc, "saleId");
        Sale sale = saleRepository.findById(id).orElseGet(() -> Sale.builder()
                .saleId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(sale.getShopId()) && sale.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        sale.setShopId(shopId);
        sale.setCustomerId(str(doc, "customerId"));
        sale.setDiscount(dbl(doc, "discount"));
        sale.setVatPercent(dbl(doc, "vatPercent"));
        sale.setCharge(dbl(doc, "charge"));
        sale.setTotalAmount(dbl(doc, "totalAmount"));
        sale.setPaidAmount(dbl(doc, "paidAmount"));
        sale.setDueAmount(dbl(doc, "dueAmount"));
        sale.setPaymentStatus(str(doc, "paymentStatus"));
        sale.setCreatedAt(time(doc, "createdAt"));
        sale.setDeleted(bool(doc, "isDeleted"));
        sale.setUpdatedAt(time(doc, "updatedAt"));
        saleRepository.save(sale);

        saleItemRepository.deleteBySaleId(id);
        Object items = doc.get("items");
        if (items instanceof List<?> list) {
            for (Object itemObj : list) {
                if (!(itemObj instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) itemMap;
                saleItemRepository.save(SaleItem.builder()
                        .id(Ids.uuid())
                        .saleId(id)
                        .productId(nul(item, "productId"))
                        .name(str(item, "name"))
                        .qty(dbl(item, "qty"))
                        .unitPrice(dbl(item, "unitPrice"))
                        .purchasePrice(dbl(item, "purchasePrice"))
                        .build());
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

    private void upsertPayment(String shopId, Map<String, Object> doc) {
        String id = str(doc, "paymentId");
        Payment payment = paymentRepository.findById(id).orElseGet(() -> Payment.builder()
                .paymentId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(payment.getShopId()) && payment.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        payment.setShopId(shopId);
        payment.setCustomerId(str(doc, "customerId"));
        payment.setSaleId(nul(doc, "saleId"));
        payment.setAmount(dbl(doc, "amount"));
        payment.setPaymentMethod(str(doc, "paymentMethod"));
        payment.setReference(nul(doc, "reference"));
        payment.setCreatedAt(time(doc, "createdAt"));
        payment.setDeleted(bool(doc, "isDeleted"));
        payment.setUpdatedAt(time(doc, "updatedAt"));
        paymentRepository.save(payment);
    }

    private void upsertPurchase(String shopId, Map<String, Object> doc) {
        String id = str(doc, "purchaseId");
        Purchase purchase = purchaseRepository.findById(id).orElseGet(() -> Purchase.builder()
                .purchaseId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(purchase.getShopId()) && purchase.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        purchase.setShopId(shopId);
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

    private void upsertExpense(String shopId, Map<String, Object> doc) {
        String id = str(doc, "expenseId");
        Expense expense = expenseRepository.findById(id).orElseGet(() -> Expense.builder()
                .expenseId(id).shopId(shopId).updatedAt(LocalDateTime.now()).build());
        if (!shopId.equals(expense.getShopId()) && expense.getShopId() != null) {
            throw BusinessException.forbidden("Document belongs to another shop");
        }
        expense.setShopId(shopId);
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
        json.put("category", p.getCategory());
        json.put("unit", p.getUnit());
        json.put("imagePath", p.getImagePath());
        json.put("buyingPrice", p.getBuyingPrice());
        json.put("purchasePrice", p.getPurchasePrice());
        json.put("sellingPrice", p.getSellingPrice());
        json.put("stockQty", p.getStockQty());
        json.put("lowStockAlert", p.getLowStockAlert());
        json.put("shopId", p.getShopId());
        json.put("updatedAt", p.getUpdatedAt().format(ISO));
        json.put("isDeleted", p.isDeleted());
        return json;
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
        json.put("shopId", c.getShopId());
        json.put("updatedAt", c.getUpdatedAt().format(ISO));
        json.put("isDeleted", c.isDeleted());
        return json;
    }

    private Map<String, Object> saleJson(Sale s) {
        List<Map<String, Object>> items = saleItemRepository.findBySaleId(s.getSaleId()).stream().map(i -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", i.getProductId());
            m.put("name", i.getName());
            m.put("qty", i.getQty());
            m.put("unitPrice", i.getUnitPrice());
            m.put("purchasePrice", i.getPurchasePrice());
            return m;
        }).toList();
        Map<String, Object> json = new HashMap<>();
        json.put("saleId", s.getSaleId());
        json.put("customerId", s.getCustomerId());
        json.put("items", items);
        json.put("discount", s.getDiscount());
        json.put("vatPercent", s.getVatPercent());
        json.put("charge", s.getCharge());
        json.put("totalAmount", s.getTotalAmount());
        json.put("paidAmount", s.getPaidAmount());
        json.put("dueAmount", s.getDueAmount());
        json.put("paymentStatus", s.getPaymentStatus());
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
        json.put("updatedAt", e.getUpdatedAt().format(ISO));
        json.put("isDeleted", e.isDeleted());
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
            case "expenses" -> "expenseId";
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
}
