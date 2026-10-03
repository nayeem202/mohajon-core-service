package com.haalkhata.feature.customer.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.customer.domain.Customer;
import com.haalkhata.feature.customer.domain.CustomerLedgerEntry;
import com.haalkhata.feature.customer.infrastructure.CustomerLedgerEntryRepository;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.customer.infrastructure.DueRecoveryPlanRepository;
import com.haalkhata.feature.customer.infrastructure.DueRecoveryFollowUpRepository;
import com.haalkhata.feature.payment.infrastructure.PaymentRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
public class CustomerService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final CustomerRepository customerRepository;
    private final CustomerLedgerEntryRepository ledgerRepository;
    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final DueRecoveryPlanRepository duePlanRepository;
    private final DueRecoveryFollowUpRepository followUpRepository;

    public record UpsertRequest(String customerId, String name, String phone, String email,
                                String address, String notes, String imagePath,
                                Double totalSpent, String tag, Double creditLimit, Boolean isDeleted) {
    }

    public record LedgerRequest(String type, double amount, String note) {
    }

    public record MergeRequest(String primaryId, String duplicateId) {
    }

    @Transactional
    public Map<String, Object> merge(CurrentUser user, MergeRequest request) {
        if (request.primaryId() == null || request.duplicateId() == null
                || request.primaryId().isBlank() || request.duplicateId().isBlank()
                || request.primaryId().equals(request.duplicateId())) {
            throw BusinessException.badRequest("Primary and duplicate customers must be different");
        }
        Customer primary = customerRepository
                .findByCustomerIdAndShopIdAndIsDeletedFalse(request.primaryId(), user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Primary customer not found"));
        var duplicateRecord = customerRepository.findById(request.duplicateId())
                .filter(item -> user.getShopId().equals(item.getShopId()))
                .orElseThrow(() -> BusinessException.notFound("Duplicate customer not found"));
        if (duplicateRecord.isDeleted()) {
            return Map.of("primaryId", primary.getCustomerId(),
                    "duplicateId", duplicateRecord.getCustomerId(),
                    "alreadyMerged", true);
        }
        Customer duplicate = customerRepository
                .findByCustomerIdAndShopIdAndIsDeletedFalse(request.duplicateId(), user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Duplicate customer not found"));
        LocalDateTime now = LocalDateTime.now();
        int sales = saleRepository.reassignCustomer(user.getShopId(), duplicate.getCustomerId(), primary.getCustomerId(), now);
        int payments = paymentRepository.reassignCustomer(user.getShopId(), duplicate.getCustomerId(), primary.getCustomerId(), now);
        int ledger = ledgerRepository.reassignCustomer(user.getShopId(), duplicate.getCustomerId(), primary.getCustomerId(), now);
        int duePlans = mergeDuePlan(user.getShopId(), duplicate.getCustomerId(), primary.getCustomerId(), now);
        int followUps = followUpRepository.reassignCustomer(user.getShopId(), duplicate.getCustomerId(), primary.getCustomerId(), now);
        primary.setTotalSpent(primary.getTotalSpent() + duplicate.getTotalSpent());
        primary.setLoyaltyPoints(Math.max(0, primary.getLoyaltyPoints() + duplicate.getLoyaltyPoints()));
        primary.setCreditLimit(Math.max(primary.getCreditLimit(), duplicate.getCreditLimit()));
        primary.setUpdatedAt(now);
        customerRepository.save(primary);
        duplicate.setDeleted(true);
        duplicate.setUpdatedAt(now);
        customerRepository.save(duplicate);
        log.info("CUSTOMER_MERGED shopId={} primary={} duplicate={} sales={} payments={} ledger={}",
                user.getShopId(), primary.getCustomerId(), duplicate.getCustomerId(), sales, payments, ledger);
        return Map.of("primaryId", primary.getCustomerId(), "duplicateId", duplicate.getCustomerId(),
                "salesRelinked", sales, "paymentsRelinked", payments, "ledgerRelinked", ledger,
                "duePlansRelinked", duePlans, "followUpsRelinked", followUps);
    }

    private int mergeDuePlan(String shopId, String source, String target, LocalDateTime now) {
        var sourcePlan = duePlanRepository.findByShopIdAndCustomerId(shopId, source).orElse(null);
        if (sourcePlan == null) return 0;
        var targetPlan = duePlanRepository.findByShopIdAndCustomerId(shopId, target).orElse(null);
        if (targetPlan == null) {
            sourcePlan.setCustomerId(target);
            sourcePlan.setUpdatedAt(now);
            duePlanRepository.save(sourcePlan);
            return 1;
        }
        if (targetPlan.getPromisedPaymentDate() == null) {
            targetPlan.setPromisedPaymentDate(sourcePlan.getPromisedPaymentDate());
        }
        if (targetPlan.getSnoozedUntil() == null) {
            targetPlan.setSnoozedUntil(sourcePlan.getSnoozedUntil());
        }
        targetPlan.setUpdatedAt(now);
        duePlanRepository.save(targetPlan);
        duePlanRepository.delete(sourcePlan);
        return 1;
    }

    public List<Map<String, Object>> list(CurrentUser user) {
        return customerRepository.findByShopIdAndIsDeletedFalseOrderByNameAsc(user.getShopId())
                .stream().map(this::toJson).toList();
    }

    public Map<String, Object> get(CurrentUser user, String customerId) {
        return customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(customerId, user.getShopId())
                .map(this::toJson)
                .orElseThrow(() -> BusinessException.notFound("Customer not found"));
    }

    @Transactional
    public Map<String, Object> upsert(CurrentUser user, UpsertRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw BusinessException.badRequest("Customer name is required");
        }
        if (request.totalSpent() != null
                && (!Double.isFinite(request.totalSpent()) || request.totalSpent() < 0)) {
            throw BusinessException.badRequest("Total spent must be a valid non-negative amount");
        }
        if (request.creditLimit() != null
                && (!Double.isFinite(request.creditLimit()) || request.creditLimit() < 0)) {
            throw BusinessException.badRequest("Credit limit must be a valid non-negative amount");
        }
        Customer customer;
        boolean isNew = request.customerId() == null || request.customerId().isBlank();
        if (isNew) {
            customer = Customer.builder()
                    .customerId(Ids.uuid())
                    .shopId(user.getShopId())
                    .totalSpent(0)
                    .creditLimit(0)
                    .isDeleted(false)
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            customer = customerRepository.findById(request.customerId())
                    .filter(c -> c.getShopId().equals(user.getShopId()))
                    .orElseThrow(() -> BusinessException.notFound("Customer not found"));
        }
        customer.setName(request.name() == null ? customer.getName() : request.name().trim());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setAddress(request.address());
        customer.setNotes(request.notes());
        customer.setImagePath(request.imagePath());
        customer.setTotalSpent(request.totalSpent() == null ? customer.getTotalSpent() : request.totalSpent());
        customer.setTag(request.tag());
        if (request.creditLimit() != null) {
            customer.setCreditLimit(request.creditLimit());
        }
        customer.setDeleted(request.isDeleted() != null && request.isDeleted());
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);
        log.info("CUSTOMER_{} customerId={} shopId={} name={}",
                isNew ? "CREATED" : "UPDATED", customer.getCustomerId(), user.getShopId(), customer.getName());
        return toJson(customer);
    }

    @Transactional
    public void delete(CurrentUser user, String customerId) {
        Customer customer = customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(customerId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Customer not found"));
        customer.setDeleted(true);
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);
        log.info("CUSTOMER_DELETED customerId={} shopId={}", customerId, user.getShopId());
    }

    @Transactional
    public Map<String, Object> addLedgerEntry(CurrentUser user, String customerId, LedgerRequest request) {
        if (request == null || !Double.isFinite(request.amount()) || request.amount() < 0) {
            throw BusinessException.badRequest("Ledger amount must be a valid non-negative amount");
        }
        customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(customerId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Customer not found"));
        String type = request.type().toUpperCase();
        if (!type.equals("DEBIT") && !type.equals("CREDIT")) {
            throw BusinessException.badRequest("Ledger type must be DEBIT or CREDIT");
        }
        CustomerLedgerEntry entry = CustomerLedgerEntry.builder()
                .entryId(Ids.uuid())
                .shopId(user.getShopId())
                .customerId(customerId)
                .type(type)
                .amount(request.amount())
                .note(request.note())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        ledgerRepository.save(entry);
        log.info("LEDGER_ENTRY_ADDED customerId={} type={} amount={}", customerId, type, request.amount());
        return toJson(entry);
    }

    public List<Map<String, Object>> ledger(CurrentUser user, String customerId) {
        return ledgerRepository.findByShopIdAndCustomerIdAndIsDeletedFalseOrderByCreatedAtAsc(
                        user.getShopId(), customerId)
                .stream().map(this::toJson).toList();
    }

    public Map<String, Object> toJson(Customer c) {
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

    public Map<String, Object> toJson(CustomerLedgerEntry e) {
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
}
