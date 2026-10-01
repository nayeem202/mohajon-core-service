package com.haalkhata.feature.vendor.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.core.util.TimeUtil;
import com.haalkhata.feature.vendor.domain.Vendor;
import com.haalkhata.feature.vendor.domain.VendorPayment;
import com.haalkhata.feature.vendor.domain.VendorPurchase;
import com.haalkhata.feature.vendor.infrastructure.VendorPaymentRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorPurchaseRepository;
import com.haalkhata.feature.vendor.infrastructure.VendorRepository;
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
public class VendorService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final VendorRepository vendorRepository;
    private final VendorPurchaseRepository purchaseRepository;
    private final VendorPaymentRepository paymentRepository;

    public record UpsertVendorRequest(String vendorId, String companyName, String contactPerson,
                                      String phone, String email, String category, String address,
                                      String products, Double rating, Integer totalOrders,
                                      Double totalSpent, String status, Boolean isDeleted) {
    }

    public record PurchaseRequest(String purchaseId, double amount, String date, String status,
                                  String notes, String itemsSupplied) {
    }

    public record PaymentRequest(String paymentId, double amount, String date, String method,
                                 String reference, String notes) {
    }

    public List<Map<String, Object>> list(CurrentUser user) {
        return vendorRepository.findByShopIdAndIsDeletedFalseOrderByCompanyNameAsc(user.getShopId())
                .stream().map(this::vendorJson).toList();
    }

    public Map<String, Object> get(CurrentUser user, String vendorId) {
        return vendorRepository.findByVendorIdAndShopIdAndIsDeletedFalse(vendorId, user.getShopId())
                .map(this::vendorJson)
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));
    }

    @Transactional
    public Map<String, Object> upsert(CurrentUser user, UpsertVendorRequest request) {
        Vendor vendor;
        boolean isNew = request.vendorId() == null || request.vendorId().isBlank();
        if (isNew) {
            vendor = Vendor.builder()
                    .vendorId(Ids.uuid())
                    .shopId(user.getShopId())
                    .rating(0)
                    .totalOrders(0)
                    .totalSpent(0)
                    .pendingAmount(0)
                    .status("ACTIVE")
                    .isDeleted(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else {
            vendor = vendorRepository.findByVendorIdAndShopIdAndIsDeletedFalse(request.vendorId(), user.getShopId())
                    .orElseThrow(() -> BusinessException.notFound("Vendor not found"));
        }
        vendor.setCompanyName(request.companyName() == null ? vendor.getCompanyName() : request.companyName().trim());
        vendor.setContactPerson(request.contactPerson());
        vendor.setPhone(request.phone());
        vendor.setEmail(request.email());
        vendor.setCategory(request.category());
        vendor.setAddress(request.address());
        vendor.setProducts(request.products());
        if (request.rating() != null) {
            vendor.setRating(request.rating());
        }
        if (request.totalOrders() != null) {
            vendor.setTotalOrders(request.totalOrders());
        }
        if (request.totalSpent() != null) {
            vendor.setTotalSpent(request.totalSpent());
        }
        if (request.status() != null) {
            vendor.setStatus(request.status().toUpperCase());
        }
        vendor.setDeleted(request.isDeleted() != null && request.isDeleted());
        vendor.setUpdatedAt(LocalDateTime.now());
        vendorRepository.save(vendor);
        log.info("VENDOR_{} vendorId={} shopId={} company={}",
                isNew ? "CREATED" : "UPDATED", vendor.getVendorId(), user.getShopId(), vendor.getCompanyName());
        return vendorJson(vendor);
    }

    @Transactional
    public void delete(CurrentUser user, String vendorId) {
        Vendor vendor = vendorRepository.findByVendorIdAndShopIdAndIsDeletedFalse(vendorId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));
        vendor.setDeleted(true);
        vendor.setUpdatedAt(LocalDateTime.now());
        vendorRepository.save(vendor);
        log.info("VENDOR_DELETED vendorId={} shopId={}", vendorId, user.getShopId());
    }

    @Transactional
    public Map<String, Object> addPurchase(CurrentUser user, String vendorId, PurchaseRequest request) {
        Vendor vendor = vendorRepository.findByVendorIdAndShopIdAndIsDeletedFalse(vendorId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));
        LocalDateTime now = LocalDateTime.now();
        VendorPurchase purchase = VendorPurchase.builder()
                .purchaseId(request.purchaseId() == null || request.purchaseId().isBlank()
                        ? Ids.uuid() : request.purchaseId())
                .shopId(user.getShopId())
                .vendorId(vendorId)
                .amount(request.amount())
                .date(request.date() == null || request.date().isBlank()
                        ? now : TimeUtil.parse(request.date(), now))
                .status(request.status() == null || request.status().isBlank()
                        ? "PENDING" : request.status().toUpperCase())
                .notes(request.notes())
                .itemsSupplied(request.itemsSupplied())
                .isDeleted(false)
                .updatedAt(now)
                .build();
        purchaseRepository.save(purchase);
        vendor.setTotalOrders(vendor.getTotalOrders() + 1);
        vendor.setTotalSpent(vendor.getTotalSpent() + request.amount());
        vendor.setPendingAmount(vendor.getPendingAmount() + request.amount());
        vendor.setLastPurchaseDate(purchase.getDate());
        vendor.setUpdatedAt(now);
        vendorRepository.save(vendor);
        log.info("VENDOR_PURCHASE_ADDED vendorId={} amount={}", vendorId, request.amount());
        return vendorJson(vendor);
    }

    @Transactional
    public Map<String, Object> addPayment(CurrentUser user, String vendorId, PaymentRequest request) {
        Vendor vendor = vendorRepository.findByVendorIdAndShopIdAndIsDeletedFalse(vendorId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));
        LocalDateTime now = LocalDateTime.now();
        VendorPayment payment = VendorPayment.builder()
                .paymentId(request.paymentId() == null || request.paymentId().isBlank()
                        ? Ids.uuid() : request.paymentId())
                .shopId(user.getShopId())
                .vendorId(vendorId)
                .amount(request.amount())
                .date(request.date() == null || request.date().isBlank()
                        ? now : TimeUtil.parse(request.date(), now))
                .method(request.method())
                .reference(request.reference())
                .notes(request.notes())
                .isDeleted(false)
                .updatedAt(now)
                .build();
        paymentRepository.save(payment);
        vendor.setPendingAmount(Math.max(0, vendor.getPendingAmount() - request.amount()));
        vendor.setUpdatedAt(now);
        vendorRepository.save(vendor);
        log.info("VENDOR_PAYMENT_ADDED vendorId={} amount={} method={}", vendorId, request.amount(), request.method());
        return vendorJson(vendor);
    }

    public List<Map<String, Object>> purchases(CurrentUser user, String vendorId) {
        return purchaseRepository.findByShopIdAndVendorIdAndIsDeletedFalseOrderByDateDesc(user.getShopId(), vendorId)
                .stream().map(p -> {
                    Map<String, Object> json = new HashMap<>();
                    json.put("purchaseId", p.getPurchaseId());
                    json.put("vendorId", p.getVendorId());
                    json.put("amount", p.getAmount());
                    json.put("date", p.getDate().format(ISO));
                    json.put("status", p.getStatus());
                    json.put("notes", p.getNotes());
                    json.put("itemsSupplied", p.getItemsSupplied());
                    json.put("updatedAt", p.getUpdatedAt().format(ISO));
                    json.put("isDeleted", p.isDeleted());
                    return json;
                }).toList();
    }

    public List<Map<String, Object>> payments(CurrentUser user, String vendorId) {
        return paymentRepository.findByShopIdAndVendorIdAndIsDeletedFalseOrderByDateDesc(user.getShopId(), vendorId)
                .stream().map(p -> {
                    Map<String, Object> json = new HashMap<>();
                    json.put("paymentId", p.getPaymentId());
                    json.put("vendorId", p.getVendorId());
                    json.put("amount", p.getAmount());
                    json.put("date", p.getDate().format(ISO));
                    json.put("method", p.getMethod());
                    json.put("reference", p.getReference());
                    json.put("notes", p.getNotes());
                    json.put("updatedAt", p.getUpdatedAt().format(ISO));
                    json.put("isDeleted", p.isDeleted());
                    return json;
                }).toList();
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
        json.put("isDeleted", v.isDeleted());
        json.put("createdAt", v.getCreatedAt().format(ISO));
        json.put("updatedAt", v.getUpdatedAt().format(ISO));
        return json;
    }
}
