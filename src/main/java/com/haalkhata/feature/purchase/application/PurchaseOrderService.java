package com.haalkhata.feature.purchase.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.purchase.domain.PurchaseOrder;
import com.haalkhata.feature.purchase.domain.PurchaseOrderItem;
import com.haalkhata.feature.purchase.infrastructure.PurchaseOrderItemRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    public record ItemRequest(String productId, String name, double qty, double unitCost,
                              String batchNumber, java.time.LocalDate expiryDate) {
    }

    public record CreateRequest(String orderId, String supplierId, List<ItemRequest> items,
                                String status, String notes, double totalAmount,
                                Map<String, Double> receivedQty, Boolean isDeleted, String updatedAt,
                                String createdAt) {
    }

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository itemRepository;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(CurrentUser user) {
        return orderRepository.findByShopIdAndBranchIdOrderByCreatedAtDesc(user.getShopId(), user.getBranchId())
                .stream().map(this::json).toList();
    }

    @Transactional
    public Map<String, Object> create(CurrentUser user, CreateRequest request) {
        if (request == null || request.orderId() == null || request.orderId().isBlank()) {
            throw BusinessException.badRequest("orderId is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw BusinessException.badRequest("At least one purchase order item is required");
        }
        if (!Double.isFinite(request.totalAmount()) || request.totalAmount() < 0) {
            throw BusinessException.badRequest("Purchase order total must be valid");
        }
        double authoritativeTotal = 0;
        Map<String, Double> orderedByProduct = new HashMap<>();
        for (ItemRequest item : request.items()) {
            if (item.productId() == null || item.productId().isBlank()
                    || !Double.isFinite(item.qty()) || item.qty() <= 0
                    || !Double.isFinite(item.unitCost()) || item.unitCost() < 0) {
                throw BusinessException.badRequest("Invalid purchase order item");
            }
            authoritativeTotal += item.qty() * item.unitCost();
            orderedByProduct.merge(item.productId().trim(), item.qty(), Double::sum);
        }
        if (Math.abs(authoritativeTotal - request.totalAmount()) > 0.01) {
            throw BusinessException.badRequest("Purchase order total does not match its items");
        }
        String status = request.status() == null || request.status().isBlank()
                ? "DRAFT" : request.status().trim().toUpperCase();
        if (!List.of("DRAFT", "ORDERED", "PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED").contains(status)) {
            throw BusinessException.badRequest("Invalid purchase order status");
        }
        if (request.receivedQty() != null) {
            for (Map.Entry<String, Double> entry : request.receivedQty().entrySet()) {
                Double ordered = orderedByProduct.get(entry.getKey());
                if (ordered == null || entry.getValue() == null
                        || !Double.isFinite(entry.getValue()) || entry.getValue() < 0
                        || entry.getValue() > ordered + 0.000001) {
                    throw BusinessException.badRequest("Received quantity exceeds the purchase order");
                }
            }
        }
        var existing = orderRepository.findByOrderIdAndShopIdAndBranchId(request.orderId(), user.getShopId(), user.getBranchId());
        if (existing.isPresent()) {
            PurchaseOrder order = existing.get();
            LocalDateTime incomingUpdated = parseTime(request.updatedAt());
            if (incomingUpdated.isAfter(order.getUpdatedAt())) {
                order.setStatus(status);
                order.setReceivedQtyJson(writeQty(request.receivedQty()));
                order.setDeleted(Boolean.TRUE.equals(request.isDeleted()));
                order.setUpdatedAt(incomingUpdated);
                orderRepository.save(order);
            }
            return json(order);
        }
        var now = LocalDateTime.now();
        var order = orderRepository.save(PurchaseOrder.builder()
                .orderId(request.orderId().trim())
                .shopId(user.getShopId())
                .branchId(user.getBranchId())
                .supplierId(blankToNull(request.supplierId()))
                .status(status)
                .totalAmount(Math.max(0, request.totalAmount()))
                .notes(trim(request.notes(), 500))
                .createdAt(parseTime(request.createdAt()))
                .updatedAt(parseTime(request.updatedAt()))
                .receivedQtyJson(writeQty(request.receivedQty()))
                .isDeleted(Boolean.TRUE.equals(request.isDeleted()))
                .build());
        itemRepository.saveAll(request.items().stream().map(item -> PurchaseOrderItem.builder()
                .itemId(java.util.UUID.randomUUID().toString())
                .orderId(order.getOrderId())
                .productId(item.productId().trim())
                .name(trim(item.name(), 200))
                .qty(item.qty())
                .unitCost(item.unitCost())
                .batchNumber(trim(item.batchNumber(), 100))
                .expiryDate(item.expiryDate())
                .build()).toList());
        return json(order);
    }

    private Map<String, Object> json(PurchaseOrder order) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("orderId", order.getOrderId());
        out.put("shopId", order.getShopId());
        out.put("branchId", order.getBranchId());
        out.put("supplierId", order.getSupplierId() == null ? "" : order.getSupplierId());
        out.put("status", order.getStatus());
        out.put("totalAmount", order.getTotalAmount());
        out.put("notes", order.getNotes() == null ? "" : order.getNotes());
        out.put("createdAt", order.getCreatedAt());
        out.put("updatedAt", order.getUpdatedAt());
        out.put("isDeleted", order.isDeleted());
        out.put("receivedQty", readQty(order.getReceivedQtyJson()));
        out.put("items", itemRepository.findByOrderId(order.getOrderId()).stream().map(item -> {
            Map<String, Object> json = new java.util.LinkedHashMap<>();
            json.put("productId", item.getProductId());
            json.put("name", item.getName());
            json.put("qty", item.getQty());
            json.put("unitCost", item.getUnitCost());
            json.put("batchNumber", item.getBatchNumber() == null ? "" : item.getBatchNumber());
            json.put("expiryDate", item.getExpiryDate());
            return json;
        }).toList());
        return out;
    }

    private String writeQty(Map<String, Double> value) {
        try { return mapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException e) { throw BusinessException.badRequest("Invalid received quantity map"); }
    }
    private Map<String, Double> readQty(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }
    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return LocalDateTime.now();
        try { return LocalDateTime.parse(value); } catch (Exception e) { return LocalDateTime.now(); }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String result = value.trim();
        return result.substring(0, Math.min(max, result.length()));
    }
}
