package com.haalkhata.feature.sale.returning.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.core.util.MoneyUtil;
import com.haalkhata.feature.guardian.application.GuardianService;
import com.haalkhata.feature.product.infrastructure.ProductBatchRepository;
import com.haalkhata.feature.product.infrastructure.ProductRepository;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.sale.domain.SaleItem;
import com.haalkhata.feature.sale.domain.SaleItemBatchAllocation;
import com.haalkhata.feature.sale.domain.Sale;
import com.haalkhata.feature.sale.infrastructure.SaleItemBatchAllocationRepository;
import com.haalkhata.feature.sale.infrastructure.SaleItemRepository;
import com.haalkhata.feature.sale.infrastructure.SaleRepository;
import com.haalkhata.feature.sale.returning.domain.SaleReturn;
import com.haalkhata.feature.sale.returning.domain.SaleReturnItem;
import com.haalkhata.feature.sale.returning.infrastructure.SaleReturnItemRepository;
import com.haalkhata.feature.sale.returning.infrastructure.SaleReturnRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaleReturnService {
    private final SaleReturnRepository returnRepository;
    private final SaleReturnItemRepository returnItemRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleItemBatchAllocationRepository allocationRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository productBatchRepository;
    private final CustomerRepository customerRepository;
    private final GuardianService guardianService;

    public record ReturnItemRequest(String saleItemId, double qty) {}
    public record CreateReturnRequest(String returnId, String saleId, List<ReturnItemRequest> items,
                                      String refundMethod, String reason) {}

    public List<Map<String, Object>> history(CurrentUser user) {
        return returnRepository.findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getShopId())
                .stream().filter(item -> user.getBranchId().equals(item.getBranchId()))
                .map(this::toJson).toList();
    }

    @Transactional
    public Map<String, Object> create(CurrentUser user, CreateReturnRequest request) {
        if (request.returnId() != null && !request.returnId().isBlank()) {
            var existing = returnRepository.findByReturnIdAndShopIdAndBranchIdAndIsDeletedFalse(
                    request.returnId(), user.getShopId(), user.getBranchId());
            if (existing.isPresent()) return toJson(existing.get());
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw BusinessException.badRequest("Select at least one item to return");
        }
        if (request.refundMethod() == null || request.refundMethod().isBlank()) {
            throw BusinessException.badRequest("Refund method is required");
        }
        String refundMethod = request.refundMethod().trim().toUpperCase();
        if (!List.of("CASH", "BKASH", "NAGAD", "BANK", "OTHER").contains(refundMethod)) {
            throw BusinessException.badRequest("Unsupported refund method");
        }
        Sale sale = saleRepository.findBySaleIdAndShopIdAndBranchIdAndIsDeletedFalse(
                        request.saleId(), user.getShopId(), user.getBranchId())
                .filter(item -> user.getBranchId().equals(item.getBranchId()))
                .orElseThrow(() -> BusinessException.notFound("Sale not found in the active branch"));
        Map<String, SaleItem> sold = new HashMap<>();
        for (SaleItem item : saleItemRepository.findBySaleId(sale.getSaleId())) sold.put(item.getId(), item);
        Map<String, Double> alreadyReturned = new HashMap<>();
        for (SaleReturn previous : returnRepository.findByShopIdAndIsDeletedFalseAndSaleId(
                user.getShopId(), sale.getSaleId())) {
            for (SaleReturnItem item : returnItemRepository.findByReturnId(previous.getReturnId())) {
                alreadyReturned.merge(item.getSaleItemId(), item.getQty(), Double::sum);
            }
        }
        List<SaleReturnItem> returnedItems = new ArrayList<>();
        double refund = 0;
        double subtotal = saleItemRepository.findBySaleId(sale.getSaleId()).stream()
                .mapToDouble(item -> item.getQty() * item.getUnitPrice()).sum();
        double netLineRatio = subtotal <= 0 ? 1d : sale.getTotalAmount() / subtotal;
        double originalSaleTotal = sale.getTotalAmount();
        LocalDateTime now = LocalDateTime.now();
        for (ReturnItemRequest requestItem : request.items()) {
            if (requestItem.qty() <= 0 || requestItem.qty() != Math.floor(requestItem.qty())) {
                throw BusinessException.badRequest("Return quantity must be a positive whole number");
            }
            SaleItem source = sold.get(requestItem.saleItemId());
            if (source == null) throw BusinessException.badRequest("Sale item does not belong to this sale");
            double remaining = source.getQty() - alreadyReturned.getOrDefault(source.getId(), 0d);
            if (requestItem.qty() > remaining) {
                throw BusinessException.badRequest("Return quantity exceeds the remaining sale quantity");
            }
            refund = MoneyUtil.round2(refund + requestItem.qty() * source.getUnitPrice() * netLineRatio);
            if (source.getProductId() != null && !source.getProductId().isBlank()) {
                if (productRepository.incrementStock(source.getProductId(), user.getShopId(), user.getBranchId(),
                        (int) requestItem.qty(), now) == 0) {
                    throw BusinessException.badRequest("Product stock could not be restored");
                }
                restoreBatchStock(user, source.getId(), (int) requestItem.qty(),
                        alreadyReturned.getOrDefault(source.getId(), 0d).intValue(), now);
            }
            returnedItems.add(SaleReturnItem.builder()
                    .returnItemId(Ids.uuid())
                    .returnId(request.returnId())
                    .saleItemId(source.getId())
                    .productId(source.getProductId())
                    .name(source.getName())
                    .qty(requestItem.qty())
                    .unitPrice(source.getUnitPrice())
                    .purchasePrice(source.getPurchasePrice())
                    .build());
        }
        String returnId = request.returnId() == null || request.returnId().isBlank()
                ? Ids.uuid() : request.returnId();
        SaleReturn saleReturn = SaleReturn.builder()
                .returnId(returnId).shopId(user.getShopId()).branchId(user.getBranchId())
                .saleId(sale.getSaleId()).customerId(sale.getCustomerId())
                .refundAmount(refund).refundMethod(refundMethod)
                .reason(request.reason() == null ? null : request.reason().trim())
                .staffId(user.getUserId()).createdAt(now).updatedAt(now).isDeleted(false).build();
        double newTotal = MoneyUtil.round2(Math.max(0, sale.getTotalAmount() - refund));
        double newPaid = MoneyUtil.round2(Math.min(sale.getPaidAmount(), newTotal));
        double newDue = MoneyUtil.round2(Math.max(0, newTotal - newPaid));
        sale.setTotalAmount(newTotal);
        sale.setPaidAmount(newPaid);
        sale.setDueAmount(newDue);
        sale.setPaymentStatus(newDue <= 0 ? "PAID" : (newPaid > 0 ? "PARTIAL" : "DUE"));
        sale.setUpdatedAt(now);
        saleRepository.save(sale);
        final double refundAmount = refund;
        customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(
                        sale.getCustomerId(), user.getShopId())
                .ifPresent(customer -> {
                    customer.setTotalSpent(MoneyUtil.round2(Math.max(0, customer.getTotalSpent() - refundAmount)));
                    double loyaltyRatio = originalSaleTotal <= 0 ? 1d
                            : Math.max(0d, Math.min(1d, refundAmount / originalSaleTotal));
                    int reversedEarned = (int) Math.floor(sale.getLoyaltyPointsEarned() * loyaltyRatio);
                    int restoredRedeemed = (int) Math.floor(sale.getLoyaltyPointsRedeemed() * loyaltyRatio);
                    customer.setLoyaltyPoints(Math.max(0,
                            customer.getLoyaltyPoints() - reversedEarned + restoredRedeemed));
                    customer.setUpdatedAt(now);
                    customerRepository.save(customer);
                });
        returnRepository.save(saleReturn);
        for (SaleReturnItem item : returnedItems) {
            item.setReturnId(returnId);
            returnItemRepository.save(item);
        }
        guardianService.record(user, "SALE_RETURN_CREATED", "SALE_RETURN", returnId, refund,
                Map.of("saleId", sale.getSaleId(), "itemCount", returnedItems.size(),
                        "refundMethod", saleReturn.getRefundMethod()));
        return toJson(saleReturn);
    }

    private void restoreBatchStock(CurrentUser user, String saleItemId, int quantity,
                                   int alreadyReturned, LocalDateTime now) {
        int remaining = quantity;
        int offset = alreadyReturned;
        for (SaleItemBatchAllocation allocation : allocationRepository.findBySaleItemId(saleItemId)) {
            if (remaining <= 0) break;
            if (offset >= allocation.getQuantity()) {
                offset -= allocation.getQuantity();
                continue;
            }
            int available = allocation.getQuantity() - offset;
            int take = Math.min(remaining, available);
            if (productBatchRepository.increment(allocation.getBatchId(), user.getShopId(),
                    user.getBranchId(), take, now) == 0) {
                throw BusinessException.badRequest("Batch stock could not be restored");
            }
            remaining -= take;
            offset = 0;
        }
    }

    public Map<String, Object> toJson(SaleReturn value) {
        Map<String, Object> json = new HashMap<>();
        json.put("returnId", value.getReturnId());
        json.put("shopId", value.getShopId());
        json.put("branchId", value.getBranchId());
        json.put("saleId", value.getSaleId());
        json.put("customerId", value.getCustomerId());
        json.put("refundAmount", value.getRefundAmount());
        json.put("refundMethod", value.getRefundMethod());
        json.put("reason", value.getReason());
        json.put("staffId", value.getStaffId());
        json.put("items", returnItemRepository.findByReturnId(value.getReturnId()).stream().map(item -> Map.of(
                "returnItemId", item.getReturnItemId(), "saleItemId", item.getSaleItemId(),
                "productId", item.getProductId() == null ? "" : item.getProductId(),
                "name", item.getName(), "qty", item.getQty(), "unitPrice", item.getUnitPrice(),
                "purchasePrice", item.getPurchasePrice())).toList());
        json.put("createdAt", value.getCreatedAt().toString());
        json.put("updatedAt", value.getUpdatedAt().toString());
        json.put("isDeleted", value.isDeleted());
        return json;
    }
}
