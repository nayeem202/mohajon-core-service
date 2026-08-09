package com.haalkhata.feature.sale.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.customer.domain.Customer;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.payment.domain.Payment;
import com.haalkhata.feature.payment.infrastructure.PaymentMethodRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.product.domain.Product;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
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
public class SaleService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;

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
        if (request.items() == null || request.items().isEmpty()) {
            throw BusinessException.badRequest("Sale must contain at least one item");
        }
        Customer customer = customerRepository
                .findByCustomerIdAndShopIdAndIsDeletedFalse(request.customerId(), user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Customer not found"));

        double subtotal = 0;
        List<SaleItem> items = new ArrayList<>();
        for (SaleItemRequest item : request.items()) {
            Product product = productRepository
                    .findByProductIdAndShopIdAndIsDeletedFalse(item.productId(), user.getShopId())
                    .orElseThrow(() -> BusinessException.notFound("Product not found: " + item.name()));
            if (product.getStockQty() < item.qty()) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Insufficient stock for " + product.getNameEn()
                                + " (available: " + product.getStockQty() + ")");
            }
            product.setStockQty(product.getStockQty() - (int) item.qty());
            product.setUpdatedAt(LocalDateTime.now());
            productRepository.save(product);

            double unitPrice = item.unitPrice() > 0 ? item.unitPrice() : product.getSellingPrice();
            subtotal += item.qty() * unitPrice;
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

        double discount = request.discount() == null ? 0 : request.discount();
        double vatPercent = request.vatPercent() == null ? 0 : request.vatPercent();
        double charge = request.charge() == null ? 0 : request.charge();
        double taxable = subtotal - discount;
        double vat = taxable * vatPercent / 100;
        double total = Math.max(0, taxable) + vat + charge;
        double paid = Math.min(request.paidAmount() == null ? 0 : request.paidAmount(), total);
        double due = total - paid;
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

        customer.setTotalSpent(customer.getTotalSpent() + total);
        customer.setUpdatedAt(now);
        customerRepository.save(customer);

        log.info("SALE_CREATED saleId={} shopId={} customer={} total={} paid={} due={} status={}",
                sale.getSaleId(), user.getShopId(), customer.getName(), total, paid, due, status);
        return toJson(sale);
    }

    @Transactional
    public void delete(CurrentUser user, String saleId) {
        Sale sale = saleRepository.findBySaleIdAndShopIdAndIsDeletedFalse(saleId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Sale not found"));
        sale.setDeleted(true);
        sale.setUpdatedAt(LocalDateTime.now());
        saleRepository.save(sale);
        log.info("SALE_DELETED saleId={} shopId={}", saleId, user.getShopId());
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
        json.put("shopId", s.getShopId());
        json.put("createdAt", s.getCreatedAt().format(ISO));
        json.put("updatedAt", s.getUpdatedAt().format(ISO));
        json.put("isDeleted", s.isDeleted());
        return json;
    }
}
