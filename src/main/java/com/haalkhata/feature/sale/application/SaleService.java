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
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.domain.SaleItem;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import com.haalkhata.feature.subscription.application.SubscriptionService;
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
public class SaleService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SubscriptionService subscriptionService;
    private final AppProperties props;
    private final GuardianService guardianService;

    public record SaleItemRequest(String productId, String name, double qty, double unitPrice, Double purchasePrice) {
    }

    public record CreateSaleRequest(String saleId, String customerId, List<SaleItemRequest> items,
                                    Double discount, Double vatPercent, Double charge,
                                    Double paidAmount, String paymentMethod, String paymentReference) {
    }

    public List<Map<String, Object>> history(CurrentUser user) {
        return saleRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId())
                .stream().map(this::toJson).toList();
    }

    public List<Map<String, Object>> historyForCustomer(CurrentUser user, String customerId) {
        return saleRepository.findByShopIdAndIsDeletedFalseAndCustomerIdOrderByCreatedAtDesc(
                        user.getShopId(), customerId)
                .stream().map(this::toJson).toList();
    }

    public Map<String, Object> get(CurrentUser user, String saleId) {
        Sale sale = saleRepository.findBySaleIdAndShopIdAndIsDeletedFalse(saleId, user.getShopId())
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
                    .findBySaleIdAndShopIdAndIsDeletedFalse(request.saleId(), user.getShopId());
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

        double subtotal = 0;
        List<SaleItem> items = new ArrayList<>();
        LocalDateTime stockNow = LocalDateTime.now();
        for (SaleItemRequest item : request.items()) {
            if (item.qty() <= 0) {
                throw BusinessException.badRequest("Quantity must be greater than zero for " + item.name());
            }
            if (item.qty() != Math.floor(item.qty())) {
                throw BusinessException.badRequest(
                        "Fractional quantity is not supported for " + item.name());
            }
            Product product = productRepository
                    .findByProductIdAndShopIdAndIsDeletedFalse(item.productId(), user.getShopId())
                    .orElseThrow(() -> BusinessException.notFound("Product not found: " + item.name()));
            if (productRepository.decrementStock(product.getProductId(), user.getShopId(),
                    (int) item.qty(), stockNow) == 0) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Insufficient stock for " + product.getNameEn()
                                + " (available: " + product.getStockQty() + ")");
            }

            double unitPrice = item.unitPrice() > 0 ? item.unitPrice() : product.getSellingPrice();
            subtotal += MoneyUtil.round2(item.qty() * unitPrice);
            items.add(SaleItem.builder()
                    .id(Ids.uuid())
                    .saleId(request.saleId() == null ? "" : request.saleId())
                    .productId(item.productId())
                    .name(item.name() == null ? product.getNameEn() : item.name())
                    .qty(item.qty())
                    .unitPrice(unitPrice)
                    .purchasePrice(item.purchasePrice() == null
                            ? product.getPurchasePrice() : item.purchasePrice())
                    .build());
        }

        double discount = MoneyUtil.round2(request.discount() == null ? 0 : request.discount());
        double vatPercent = request.vatPercent() == null ? 0 : request.vatPercent();
        double charge = MoneyUtil.round2(request.charge() == null ? 0 : request.charge());
        double taxable = MoneyUtil.round2(Math.max(0, subtotal - discount));
        double vat = MoneyUtil.round2(taxable * vatPercent / 100);
        double total = MoneyUtil.round2(taxable + vat + charge);
        double paid = MoneyUtil.round2(Math.min(request.paidAmount() == null ? 0 : request.paidAmount(), total));
        double due = MoneyUtil.round2(Math.max(0, total - paid));
        String status = due <= 0 ? "PAID" : (paid > 0 ? "PARTIAL" : "DUE");

        LocalDateTime now = LocalDateTime.now();
        Sale sale = Sale.builder()
                .saleId(request.saleId() == null || request.saleId().isBlank() ? Ids.uuid() : request.saleId())
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
                .staffId(user.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(false)
                .build();
        saleRepository.save(sale);

        for (SaleItem item : items) {
            item.setSaleId(sale.getSaleId());
            saleItemRepository.save(item);
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

    @Transactional
    public void delete(CurrentUser user, String saleId) {
        Sale sale = saleRepository.findBySaleIdAndShopIdAndIsDeletedFalse(saleId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Sale not found"));
        LocalDateTime now = LocalDateTime.now();

        // Restore stock to the shelves for every sold item.
        for (SaleItem item : saleItemRepository.findBySaleId(saleId)) {
            if (item.getProductId() != null && !item.getProductId().isBlank()) {
                productRepository.incrementStock(item.getProductId(), user.getShopId(),
                        (int) Math.round(item.getQty()), now);
            }
        }

        // Reverse the sale's effect on customer totals and mark any payments
        // recorded against it deleted so the ledger stays consistent.
        customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(sale.getCustomerId(), user.getShopId())
                .ifPresent(customer -> {
                    customer.setTotalSpent(MoneyUtil.round2(customer.getTotalSpent() - sale.getTotalAmount()));
                    customer.setUpdatedAt(now);
                    customerRepository.save(customer);
                });
        for (Payment payment : paymentRepository
                .findBySaleIdAndShopIdAndIsDeletedFalse(sale.getSaleId(), user.getShopId())) {
            payment.setDeleted(true);
            payment.setUpdatedAt(now);
            paymentRepository.save(payment);
        }

        sale.setDeleted(true);
        sale.setUpdatedAt(now);
        saleRepository.save(sale);
        log.info("SALE_DELETED saleId={} shopId={}", saleId, user.getShopId());
        guardianService.record(user, "DELETE", "SALE", saleId, sale.getTotalAmount(), Map.of("reason", "manual_delete"));
    }

    private void requireActiveSubscription(CurrentUser user) {
        if (props.isEnforceSubscription()
                && !subscriptionService.hasActiveSubscription(user.getShopId())) {
            throw new BusinessException("SUBSCRIPTION_REQUIRED",
                    "An active subscription is required to record sales",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
    }

    public Map<String, Object> toJson(Sale s) {
        Map<String, Object> json = new HashMap<>();
        List<Map<String, Object>> items = saleItemRepository.findBySaleId(s.getSaleId())
                .stream().map(i -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", i.getProductId());
                    m.put("name", i.getName());
                    m.put("qty", i.getQty());
                    m.put("unitPrice", i.getUnitPrice());
                    m.put("purchasePrice", i.getPurchasePrice());
                    return m;
                }).toList();
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
        json.put("paymentMethod", s.getPaymentMethod());
        json.put("staffId", s.getStaffId());
        json.put("shopId", s.getShopId());
        json.put("createdAt", s.getCreatedAt().format(ISO));
        json.put("updatedAt", s.getUpdatedAt().format(ISO));
        json.put("isDeleted", s.isDeleted());
        return json;
    }
}
