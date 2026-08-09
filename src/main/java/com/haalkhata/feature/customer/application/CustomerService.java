package com.haalkhata.feature.customer.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.customer.domain.Customer;
import com.haalkhata.feature.customer.domain.CustomerLedgerEntry;
import com.haalkhata.feature.customer.infrastructure.CustomerLedgerEntryRepository;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
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

    public record UpsertRequest(String customerId, String name, String phone, String email,
                                String address, String notes, String imagePath,
                                Double totalSpent, String tag, Boolean isDeleted) {
    }

    public record LedgerRequest(String type, double amount, String note) {
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
        Customer customer;
        boolean isNew = request.customerId() == null || request.customerId().isBlank();
        if (isNew) {
            customer = Customer.builder()
                    .customerId(Ids.uuid())
                    .shopId(user.getShopId())
                    .totalSpent(0)
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
