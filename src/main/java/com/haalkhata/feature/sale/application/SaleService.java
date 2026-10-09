package com.haalkhata.feature.sale.application;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.core.util.MoneyUtil;
import com.haalkhata.feature.customer.domain.Customer;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.guardian.application.GuardianService;
import com.haalkhata.feature.payment.domain.Payment;
import com.haalkhata.feature.payment.infrastructure.PaymentMethodRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.product.domain.Product;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.product.domain.ProductBatch;
import com.haalkhata.feature.product.infrastructure.ProductBatchRepository;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.domain.SaleItem;
import com.haalkhata.feature.sale.domain.SaleItemBatchAllocation;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleItemBatchAllocationRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import com.haalkhata.feature.subscription.application.SubscriptionService;
import java.time.LocalDateTime;
import java.time.LocalDate;
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
public class SaleService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleItemBatchAllocationRepository saleItemBatchAllocationRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository productBatchRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionService subscriptionService;
    private final AppProperties props;
    private final GuardianService guardianService;

    public record SaleItemRequest(String saleItemId, String productId, String name, double qty, double unitPrice, Double purchasePrice) {
    }

    public record CreateSaleRequest(String saleId, String customerId, List<SaleItemRequest> items,
                                    Double discount, Double vatPercent, Double charge,
                                    Double paidAmount, String paymentMethod, String paymentReference,
                                    String invoiceNumber, Integer loyaltyPointsRedeemed) {
    }

    private record BatchAllocation(String batchId, int quantity) {
    }

    public List<Map<String, Object>> history(CurrentUser user) {
        return saleRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId())
                .stream().filter(s -> user.getBranchId().equals(s.getBranchId()))
                .map(this::toJson).toList();
    }

    public List<Map<String, Object>> historyForCustomer(CurrentUser user, String customerId) {
        return saleRepository.findByShopIdAndIsDeletedFalseAndCustomerIdOrderByCreatedAtDesc(
                        user.getShopId(), customerId)
                .stream().filter(s -> user.getBranchId().equals(s.getBranchId()))
                .map(this::toJson).toList();
    }

    public Map<String, Object> get(CurrentUser user, String saleId) {
        Sale sale = saleRepository.findBySaleIdAndShopIdAndBranchIdAndIsDeletedFalse(saleId, user.getShopId(), user.getBranchId())
                .orElseThrow(() -> BusinessException.notFound("Sale not found"));
        return toJson(sale);
    }

    @Transactional
    public Map<String, Object> create(CurrentUser user, CreateSaleRequest request) {
        requireActiveSubscription(user);
        // Idempotent retry: a duplicate POST with an existing saleId returns
        // the already-created sale instead of decrementing stock twice.
        if (request.saleId() != null && !request.saleId().isBlank()) {
            var existing = saleRepository
                    .findBySaleIdAndShopIdAndBranchIdAndIsDeletedFalse(request.saleId(), user.getShopId(), user.getBranchId());
            if (existing.isPresent()) {
                log.info("SALE_IDEMPOTENT_RETRY saleId={} shopId={}", request.saleId(), user.getShopId());
                return toJson(existing.get());
            }
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw BusinessException.badRequest("Sale must contain at least one item");
        }
        Customer customer = customerRepository
                .findByCustomerIdAndShopIdAndIsDeletedFalse(request.customerId(), user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Customer not found"));

        double requestedDiscount = request.discount() == null ? 0 : request.discount();
        double requestedVatPercent = request.vatPercent() == null ? 0 : request.vatPercent();
        double requestedCharge = request.charge() == null ? 0 : request.charge();
        double requestedPaid = request.paidAmount() == null ? 0 : request.paidAmount();
        if (!Double.isFinite(requestedDiscount) || requestedDiscount < 0) {
            throw BusinessException.badRequest("Discount must be a valid non-negative amount");
        }
        if (!Double.isFinite(requestedVatPercent) || requestedVatPercent < 0 || requestedVatPercent > 100) {
            throw BusinessException.badRequest("VAT rate must be between 0 and 100 percent");
        }
        if (!Double.isFinite(requestedCharge) || requestedCharge < 0) {
            throw BusinessException.badRequest("Charge must be a valid non-negative amount");
        }
        if (!Double.isFinite(requestedPaid) || requestedPaid < 0) {
            throw BusinessException.badRequest("Paid amount must be a valid non-negative amount");
        }

        double subtotal = 0;
        List<SaleItem> items = new ArrayList<>();
        Map<SaleItem, List<BatchAllocation>> batchAllocations = new java.util.IdentityHashMap<>();
        LocalDateTime stockNow = LocalDateTime.now();
        for (SaleItemRequest item : request.items()) {
            if (!Double.isFinite(item.qty()) || item.qty() <= 0) {
                throw BusinessException.badRequest("Quantity must be greater than zero for " + item.name());
            }
            if (item.qty() != Math.floor(item.qty())) {
                throw BusinessException.badRequest(
                        "Fractional quantity is not supported for " + item.name());
            }
            if (!Double.isFinite(item.unitPrice()) || item.unitPrice() < 0) {
                throw BusinessException.badRequest("Unit price must be a valid non-negative amount for " + item.name());
            }
            if (item.purchasePrice() != null
                    && (!Double.isFinite(item.purchasePrice()) || item.purchasePrice() < 0)) {
                throw BusinessException.badRequest("Purchase price must be a valid non-negative amount for " + item.name());
            }
            Product product = productRepository
                    .findByProductIdAndShopIdAndBranchIdAndIsDeletedFalse(item.productId(), user.getShopId(), user.getBranchId())
                    .orElseThrow(() -> BusinessException.notFound("Product not found: " + item.name()));
            if (product.getExpiryDate() != null && product.getExpiryDate().isBefore(LocalDate.now())) {
                throw BusinessException.badRequest("Expired product cannot be sold: " + product.getNameEn());
            }
            if (productRepository.decrementStock(product.getProductId(), user.getShopId(), user.getBranchId(),
                    (int) item.qty(), stockNow) == 0) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Insufficient stock for " + product.getNameEn()
                                + " (available: " + product.getStockQty() + ")");
            }
            List<BatchAllocation> allocations = consumeBatchStock(
                    user.getShopId(), user.getBranchId(), item.productId(), (int) item.qty(), stockNow);

            double unitPrice = item.unitPrice() > 0 ? item.unitPrice() : product.getSellingPrice();
            subtotal += MoneyUtil.round2(item.qty() * unitPrice);
            SaleItem saleItem = SaleItem.builder()
                    .id(item.saleItemId() == null || item.saleItemId().isBlank() ? Ids.uuid() : item.saleItemId())
                    .saleId(request.saleId() == null ? "" : request.saleId())
                    .productId(item.productId())
                    .name(item.name() == null ? product.getNameEn() : item.name())
                    .qty(item.qty())
                    .unitPrice(unitPrice)
                    .purchasePrice(item.purchasePrice() == null
                            ? product.getPurchasePrice() : item.purchasePrice())
                    .build();
            items.add(saleItem);
            batchAllocations.put(saleItem, allocations);
        }

        double discount = MoneyUtil.round2(request.discount() == null ? 0 : request.discount());
        double vatPercent = request.vatPercent() == null ? 0 : request.vatPercent();
        double charge = MoneyUtil.round2(request.charge() == null ? 0 : request.charge());
        if (discount > subtotal + 0.005) {
            throw BusinessException.badRequest("Discount cannot exceed the sale subtotal");
        }
        double taxable = MoneyUtil.round2(Math.max(0, subtotal - discount));
        double vat = MoneyUtil.round2(taxable * vatPercent / 100);
        double total = MoneyUtil.round2(taxable + vat + charge);
        if (requestedPaid > total + 0.005) {
            throw BusinessException.badRequest("Paid amount cannot exceed the invoice total");
        }
        double paid = MoneyUtil.round2(requestedPaid);
        double due = MoneyUtil.round2(Math.max(0, total - paid));
        int redeemedPoints = Math.max(0, request.loyaltyPointsRedeemed() == null ? 0 : request.loyaltyPointsRedeemed());
        if (redeemedPoints > customer.getLoyaltyPoints()) {
            throw BusinessException.badRequest("Insufficient loyalty points");
        }
        int earnedPoints = (int) Math.floor(paid / 100.0);
        if (customer.getCreditLimit() > 0) {
            double existingDue = MoneyUtil.round2(
                    saleRepository.sumDueForCustomer(user.getShopId(), customer.getCustomerId()));
            if (existingDue + due > customer.getCreditLimit() + 0.005) {
                throw new BusinessException("CREDIT_LIMIT_EXCEEDED",
                        "Customer credit limit exceeded (available: "
                                + MoneyUtil.round2(customer.getCreditLimit() - existingDue) + ")");
            }
        }
        String status = due <= 0 ? "PAID" : (paid > 0 ? "PARTIAL" : "DUE");

        LocalDateTime now = LocalDateTime.now();
        Sale sale = Sale.builder()
                .saleId(request.saleId() == null || request.saleId().isBlank() ? Ids.uuid() : request.saleId())
                .invoiceNumber(invoiceNumber(request.invoiceNumber(), request.saleId()))
                .shopId(user.getShopId())
                .customerId(customer.getCustomerId())
                .discount(discount)
                .vatPercent(vatPercent)
                .charge(charge)
                .totalAmount(total)
                .paidAmount(paid)
                .dueAmount(due)
                .paymentStatus(status)
                .paymentMethod(request.paymentMethod() == null || request.paymentMethod().isBlank()
                        ? "CASH" : request.paymentMethod().toUpperCase())
                .loyaltyPointsRedeemed(redeemedPoints)
                .loyaltyPointsEarned(earnedPoints)
                .staffId(user.getUserId())
                .branchId(user.getBranchId())
                .discountStaffId(discount > 0 ? user.getUserId() : null)
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(false)
                .build();
        saleRepository.save(sale);
        customer.setLoyaltyPoints(Math.max(0, customer.getLoyaltyPoints() - redeemedPoints + earnedPoints));
        customer.setUpdatedAt(now);
        customerRepository.save(customer);

        for (SaleItem item : items) {
            item.setSaleId(sale.getSaleId());
            saleItemRepository.save(item);
            for (BatchAllocation allocation : batchAllocations.getOrDefault(item, List.of())) {
                saleItemBatchAllocationRepository.save(SaleItemBatchAllocation.builder()
                        .allocationId(Ids.uuid())
                        .saleId(sale.getSaleId())
                        .saleItemId(item.getId())
                        .batchId(allocation.batchId())
                        .quantity(allocation.quantity())
                        .createdAt(now)
                        .build());
            }
        }

        if (paid > 0) {
            String method = request.paymentMethod() == null || request.paymentMethod().isBlank()
                    ? "CASH" : request.paymentMethod().toUpperCase();
            paymentMethodRepository.findById(method)
                    .filter(PaymentMethod -> PaymentMethod.isEnabled())
                    .orElseThrow(() -> BusinessException.badRequest(
                            "Payment method " + method + " is not configured/enabled"));
            Payment payment = Payment.builder()
                    .paymentId(Ids.uuid())
                    .shopId(user.getShopId())
                    .customerId(customer.getCustomerId())
                    .saleId(sale.getSaleId())
                    .amount(paid)
                    .paymentMethod(method)
                    .reference(request.paymentReference())
                    .staffId(user.getUserId())
                    .createdAt(now)
                    .updatedAt(now)
                    .isDeleted(false)
                    .build();
            paymentRepository.save(payment);
            log.info("PAYMENT_RECORDED paymentId={} amount={} method={}", payment.getPaymentId(), paid, method);
        }

        customer.setTotalSpent(MoneyUtil.round2(customer.getTotalSpent() + total));
        customer.setUpdatedAt(now);
        customerRepository.save(customer);

        log.info("SALE_CREATED saleId={} shopId={} customer={} total={} paid={} due={} status={}",
                sale.getSaleId(), user.getShopId(), customer.getName(), total, paid, due, status);
        guardianService.record(user, "SALE_CREATED", "SALE", sale.getSaleId(), total,
                Map.of("discount", discount, "paid", paid, "due", due, "itemCount", items.size()));
        return toJson(sale);
    }

    private List<BatchAllocation> consumeBatchStock(
            String shopId, String branchId, String productId, int quantity, LocalDateTime now) {
        List<ProductBatch> batches = productBatchRepository
                .findByShopIdAndBranchIdAndProductIdAndIsDeletedFalseOrderByExpiryDateAsc(shopId, branchId, productId);
        if (batches.isEmpty()) return List.of(); // Legacy product stock has no batch ledger.
        int remaining = quantity;
        int available = batches.stream()
                .filter(batch -> batch.getExpiryDate() == null
                        || !batch.getExpiryDate().isBefore(LocalDate.now()))
                .mapToInt(ProductBatch::getQuantity)
                .sum();
        if (available < quantity) {
            throw BusinessException.badRequest("Batch stock is lower than product stock. Sync inventory before selling.");
        }
        List<BatchAllocation> allocations = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (ProductBatch batch : batches) {
            if (remaining <= 0) break;
            if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today)) continue;
            int take = Math.min(remaining, batch.getQuantity());
            if (take <= 0) continue;
            if (productBatchRepository.decrement(batch.getBatchId(), shopId, branchId, take, now) == 0) {
                throw BusinessException.badRequest("Batch stock changed. Please retry the sale.");
            }
            allocations.add(new BatchAllocation(batch.getBatchId(), take));
            remaining -= take;
        }
        if (remaining > 0) {
            throw BusinessException.badRequest("Batch stock is lower than product stock. Sync inventory before selling.");
        }
        return allocations;
    }

    @Transactional
    public void delete(CurrentUser user, String saleId) {
        Sale sale = saleRepository.findBySaleIdAndShopIdAndBranchIdAndIsDeletedFalse(saleId, user.getShopId(), user.getBranchId())
                .orElseThrow(() -> BusinessException.notFound("Sale not found"));
        LocalDateTime now = LocalDateTime.now();

        // Restore stock to the shelves for every sold item.
        for (SaleItem item : saleItemRepository.findBySaleId(saleId)) {
            if (item.getProductId() != null && !item.getProductId().isBlank()) {
                productRepository.incrementStock(item.getProductId(), user.getShopId(), user.getBranchId(),
                        (int) Math.round(item.getQty()), now);
            }
            for (SaleItemBatchAllocation allocation : saleItemBatchAllocationRepository
                    .findBySaleItemId(item.getId())) {
                productBatchRepository.increment(
                        allocation.getBatchId(), user.getShopId(),
                        sale.getBranchId() == null ? user.getBranchId() : sale.getBranchId(),
                        allocation.getQuantity(), now);
            }
        }

        // Reverse the sale's effect on customer totals and mark any payments
        // recorded against it deleted so the ledger stays consistent.
        customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(sale.getCustomerId(), user.getShopId())
                .ifPresent(customer -> {
                    customer.setTotalSpent(MoneyUtil.round2(customer.getTotalSpent() - sale.getTotalAmount()));
                    customer.setLoyaltyPoints(Math.max(0, customer.getLoyaltyPoints()
                            - sale.getLoyaltyPointsEarned() + sale.getLoyaltyPointsRedeemed()));
                    customer.setUpdatedAt(now);
                    customerRepository.save(customer);
                });
        for (Payment payment : paymentRepository
                .findBySaleIdAndShopIdAndBranchIdAndIsDeletedFalse(sale.getSaleId(), user.getShopId(), user.getBranchId())) {
            payment.setDeleted(true);
            payment.setUpdatedAt(now);
            paymentRepository.save(payment);
        }

        sale.setDeleted(true);
        sale.setVoidedByStaffId(user.getUserId());
        sale.setVoidedAt(now);
        sale.setUpdatedAt(now);
        saleRepository.save(sale);
        log.info("SALE_DELETED saleId={} shopId={}", saleId, user.getShopId());
        guardianService.record(user, "DELETE", "SALE", saleId, sale.getTotalAmount(), Map.of("reason", "manual_delete"));
    }

    private void requireActiveSubscription(CurrentUser user) {
        // Sales are part of the permanent Free tier. Premium restrictions are
        // applied centrally to the features that carry paid value (exports,
        // purchasing, business tools, staff and branches).
    }

    public Map<String, Object> toJson(Sale s) {
        Map<String, Object> json = new HashMap<>();
        List<Map<String, Object>> items = saleItemRepository.findBySaleId(s.getSaleId())
                .stream().map(i -> {
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

    private String invoiceNumber(String requested, String saleId) {
        if (requested != null && !requested.isBlank()) return requested.trim();
        String id = saleId == null || saleId.isBlank() ? Ids.uuid() : saleId;
        String compact = id.replace("-", "").toUpperCase();
        return "INV-" + compact.substring(0, Math.min(12, compact.length()));
    }
}
