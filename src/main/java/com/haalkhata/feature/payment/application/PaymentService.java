package com.haalkhata.feature.payment.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.core.util.MoneyUtil;
import com.haalkhata.feature.customer.domain.Customer;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.payment.domain.Payment;
import com.haalkhata.feature.payment.domain.PaymentMethod;
import com.haalkhata.feature.payment.infrastructure.PaymentMethodRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;

    public record AddPaymentRequest(String paymentId, String customerId, String saleId, double amount,
                                    String paymentMethod, String reference) {
    }

    public record PaymentMethodRequest(String code, String name, String label,
                                       Boolean enabled, Boolean requiresReference,
                                       Double feePercent, Integer sortOrder, String config) {
    }

    public List<Map<String, Object>> list(CurrentUser user) {
        return paymentRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId())
                .stream().map(this::toJson).toList();
    }

    public List<Map<String, Object>> listForCustomer(CurrentUser user, String customerId) {
        return paymentRepository.findByShopIdAndCustomerIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        user.getShopId(), customerId)
                .stream().map(this::toJson).toList();
    }

    @Transactional
    public Map<String, Object> addPayment(CurrentUser user, AddPaymentRequest request) {
        // Idempotent retry: a duplicate POST with an existing paymentId returns
        // the already-created payment instead of double-applying it.
        if (request.paymentId() != null && !request.paymentId().isBlank()) {
            Optional<Payment> existing = paymentRepository.findById(request.paymentId())
                    .filter(p -> p.getShopId().equals(user.getShopId()));
            if (existing.isPresent()) {
                log.info("PAYMENT_IDEMPOTENT_RETRY paymentId={} shopId={}", request.paymentId(), user.getShopId());
                return toJson(existing.get());
            }
        }

        Customer customer = customerRepository
                .findByCustomerIdAndShopIdAndIsDeletedFalse(request.customerId(), user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Customer not found"));

        String method = request.paymentMethod() == null || request.paymentMethod().isBlank()
                ? "CASH" : request.paymentMethod().toUpperCase();
        PaymentMethod paymentMethod = paymentMethodRepository.findById(method)
                .filter(PaymentMethod::isEnabled)
                .orElseThrow(() -> BusinessException.badRequest(
                        "Payment method " + method + " is not configured/enabled"));
        if (paymentMethod.isRequiresReference()
                && (request.reference() == null || request.reference().isBlank())) {
            throw BusinessException.badRequest("Reference is required for " + paymentMethod.getName());
        }

        double amount = MoneyUtil.round2(request.amount());

        Sale sale = null;
        if (request.saleId() != null && !request.saleId().isBlank()) {
            sale = saleRepository.findBySaleIdAndShopIdAndIsDeletedFalse(request.saleId(), user.getShopId())
                    .orElseThrow(() -> BusinessException.notFound("Sale not found"));
            double alreadyPaid = paymentRepository
                    .findBySaleIdAndShopIdAndIsDeletedFalse(request.saleId(), user.getShopId())
                    .stream().mapToDouble(Payment::getAmount).sum();
            double maxPayable = MoneyUtil.round2(Math.max(0, sale.getTotalAmount() - alreadyPaid));
            if (amount > maxPayable) {
                throw BusinessException.badRequest("Payment exceeds the outstanding balance for this sale "
                        + "(outstanding: " + maxPayable + ")");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Payment payment = Payment.builder()
                .paymentId(request.paymentId() == null || request.paymentId().isBlank()
                        ? Ids.uuid() : request.paymentId().trim())
                .shopId(user.getShopId())
                .customerId(customer.getCustomerId())
                .saleId(request.saleId())
                .amount(amount)
                .paymentMethod(method)
                .reference(request.reference())
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(false)
                .build();
        paymentRepository.save(payment);

        if (sale != null) {
            double newPaid = MoneyUtil.round2(paymentRepository
                    .findBySaleIdAndShopIdAndIsDeletedFalse(sale.getSaleId(), user.getShopId())
                    .stream().mapToDouble(Payment::getAmount).sum());
            double newDue = MoneyUtil.round2(Math.max(0, sale.getTotalAmount() - newPaid));
            sale.setPaidAmount(newPaid);
            sale.setDueAmount(newDue);
            sale.setPaymentStatus(newDue <= 0 ? "PAID" : "PARTIAL");
            sale.setUpdatedAt(now);
            saleRepository.save(sale);
            log.info("SALE_PAYMENT_APPLIED saleId={} paid={} due={}", sale.getSaleId(), newPaid, newDue);
        }

        double fee = request.amount() * paymentMethod.getFeePercent() / 100;
        log.info("PAYMENT_ADDED paymentId={} customer={} amount={} method={} fee={}",
                payment.getPaymentId(), customer.getName(), amount, method, MoneyUtil.round2(fee));
        return toJson(payment);
    }

    @Transactional
    public void delete(CurrentUser user, String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .filter(p -> p.getShopId().equals(user.getShopId()) && !p.isDeleted())
                .orElseThrow(() -> BusinessException.notFound("Payment not found"));
        payment.setDeleted(true);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Reverse the payment's effect on the linked sale: paid/due are
        // recomputed from the remaining active payments.
        if (payment.getSaleId() != null && !payment.getSaleId().isBlank()) {
            saleRepository.findBySaleIdAndShopIdAndIsDeletedFalse(payment.getSaleId(), user.getShopId())
                    .ifPresent(sale -> {
                        double newPaid = MoneyUtil.round2(paymentRepository
                                .findBySaleIdAndShopIdAndIsDeletedFalse(sale.getSaleId(), user.getShopId())
                                .stream().mapToDouble(Payment::getAmount).sum());
                        double newDue = MoneyUtil.round2(Math.max(0, sale.getTotalAmount() - newPaid));
                        sale.setPaidAmount(newPaid);
                        sale.setDueAmount(newDue);
                        sale.setPaymentStatus(newDue <= 0 ? "PAID" : (newPaid > 0 ? "PARTIAL" : "DUE"));
                        sale.setUpdatedAt(LocalDateTime.now());
                        saleRepository.save(sale);
                        log.info("SALE_PAYMENT_REVERSED saleId={} paid={} due={}", sale.getSaleId(), newPaid, newDue);
                    });
        }
        log.info("PAYMENT_DELETED paymentId={} shopId={}", paymentId, user.getShopId());
    }

    public Map<String, Object> toJson(Payment p) {
        Map<String, Object> json = new HashMap<>();
        json.put("paymentId", p.getPaymentId());
        json.put("customerId", p.getCustomerId());
        json.put("saleId", p.getSaleId());
        json.put("amount", MoneyUtil.round2(p.getAmount()));
        json.put("paymentMethod", p.getPaymentMethod());
        json.put("reference", p.getReference());
        json.put("shopId", p.getShopId());
        json.put("createdAt", p.getCreatedAt().format(ISO));
        json.put("updatedAt", p.getUpdatedAt().format(ISO));
        json.put("isDeleted", p.isDeleted());
        return json;
    }

    // ---------- Configurable payment methods ----------

    public List<Map<String, Object>> methods(boolean includeDisabled) {
        List<PaymentMethod> methods = includeDisabled
                ? paymentMethodRepository.findAllByOrderBySortOrderAsc()
                : paymentMethodRepository.findByEnabledTrueOrderBySortOrderAsc();
        return methods.stream().map(m -> {
            Map<String, Object> json = new HashMap<>();
            json.put("code", m.getCode());
            json.put("name", m.getName());
            json.put("label", m.getLabel());
            json.put("enabled", m.isEnabled());
            json.put("requiresReference", m.isRequiresReference());
            json.put("feePercent", m.getFeePercent());
            json.put("sortOrder", m.getSortOrder());
            json.put("config", m.getConfig());
            return json;
        }).toList();
    }

    @Transactional
    public Map<String, Object> upsertMethod(PaymentMethodRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw BusinessException.badRequest("Payment method code is required");
        }
        String code = request.code().trim().toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        PaymentMethod method = paymentMethodRepository.findById(code).orElseGet(() ->
                PaymentMethod.builder()
                        .code(code)
                        .enabled(true)
                        .requiresReference(false)
                        .feePercent(0)
                        .sortOrder(99)
                        .createdAt(now)
                        .build());
        if (request.name() != null && !request.name().isBlank()) {
            method.setName(request.name().trim());
        } else if (method.getName() == null) {
            method.setName(code);
        }
        method.setLabel(request.label() == null ? method.getName() : request.label());
        if (request.enabled() != null) {
            method.setEnabled(request.enabled());
        }
        if (request.requiresReference() != null) {
            method.setRequiresReference(request.requiresReference());
        }
        if (request.feePercent() != null) {
            method.setFeePercent(request.feePercent());
        }
        if (request.sortOrder() != null) {
            method.setSortOrder(request.sortOrder());
        }
        method.setConfig(request.config());
        method.setUpdatedAt(now);
        paymentMethodRepository.save(method);
        log.info("PAYMENT_METHOD_UPSERTED code={} enabled={} feePercent={}",
                code, method.isEnabled(), method.getFeePercent());
        return methods(true).stream().filter(m -> code.equals(m.get("code"))).findFirst().orElse(Map.of());
    }

    @Transactional
    public void deleteMethod(String code) {
        if (!paymentMethodRepository.existsById(code)) {
            throw BusinessException.notFound("Payment method not found");
        }
        paymentMethodRepository.deleteById(code);
        log.info("PAYMENT_METHOD_DELETED code={}", code);
    }
}
