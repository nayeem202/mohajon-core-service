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

    public record ReceiptItemRequest(String productId, double orderedQty, double deliveredQty,
                                     double acceptedQty, double damagedQty, double unitCost,
                                     double orderedUnitCost, String issueNote) {
    }

    public record ReceiptRequest(String receiptId, String receivedAt, List<ReceiptItemRequest> items,
                                 double invoiceAmount, String invoiceNumber, double freightAmount,
                                 double discountAmount, String attachmentPath, String notes) {
    }

    public record CreateRequest(String orderId, String supplierId, List<ItemRequest> items,
                                String status, String notes, double totalAmount,
                                Map<String, Double> receivedQty, Map<String, Double> deliveredQty,
                                List<ReceiptRequest> receipts, Boolean isDeleted, String updatedAt,
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
        Map<String, Double> deliveredQty = request.deliveredQty() == null
                ? (request.receivedQty() == null ? Map.of() : request.receivedQty())
                : request.deliveredQty();
        for (Map.Entry<String, Double> entry : deliveredQty.entrySet()) {
            Double ordered = orderedByProduct.get(entry.getKey());
            double accepted = request.receivedQty() == null ? 0
                    : request.receivedQty().getOrDefault(entry.getKey(), 0.0);
            if (ordered == null || entry.getValue() == null || !Double.isFinite(entry.getValue())
                    || entry.getValue() < accepted || entry.getValue() > ordered + 0.000001) {
                throw BusinessException.badRequest("Delivered quantity is invalid for the purchase order");
            }
        }
        validateReceipts(request.receipts(), orderedByProduct);
        Map<String, Double> receiptDelivered = receiptQuantities(request.receipts(), false);
        Map<String, Double> receiptAccepted = receiptQuantities(request.receipts(), true);
        for (String productId : orderedByProduct.keySet()) {
            double ordered = orderedByProduct.get(productId);
            if (receiptDelivered.getOrDefault(productId, 0.0) > ordered + 0.000001
                    || receiptAccepted.getOrDefault(productId, 0.0) > ordered + 0.000001) {
                throw BusinessException.badRequest("Supplier receipts exceed the purchase order quantity");
            }
        }
        var existing = orderRepository.findByOrderIdAndShopIdAndBranchId(request.orderId(), user.getShopId(), user.getBranchId());
        if (existing.isPresent()) {
            PurchaseOrder order = existing.get();
            LocalDateTime incomingUpdated = parseTime(request.updatedAt());
            List<ReceiptRequest> mergedReceipts = mergeReceipts(
                    readReceiptRecords(order.getReceiptsJson()), request.receipts());
            Map<String, Double> mergedReceived = mergeQuantities(
                    readQty(order.getReceivedQtyJson()), request.receivedQty());
            Map<String, Double> mergedDelivered = mergeQuantities(
                    readQty(order.getDeliveredQtyJson()), deliveredQty);
            if (!mergedReceipts.isEmpty()) {
                mergedReceived = mergeQuantities(mergedReceived, receiptQuantities(mergedReceipts, true));
                mergedDelivered = mergeQuantities(mergedDelivered, receiptQuantities(mergedReceipts, false));
            }
            for (Map.Entry<String, Double> entry : mergedDelivered.entrySet()) {
                if (entry.getValue() > orderedByProduct.getOrDefault(entry.getKey(), 0.0) + 0.000001) {
                    throw BusinessException.conflict(
                            "Concurrent supplier receipts exceed the remaining purchase order quantity");
                }
            }
            if (incomingUpdated.isAfter(order.getUpdatedAt())) {
                order.setStatus(status);
                order.setDeleted(Boolean.TRUE.equals(request.isDeleted()));
            }
            boolean fullyDelivered = true;
            for (Map.Entry<String, Double> entry : orderedByProduct.entrySet()) {
                if (mergedDelivered.getOrDefault(entry.getKey(), 0.0)
                        < entry.getValue() - 0.000001) {
                    fullyDelivered = false;
                    break;
                }
            }
            if (fullyDelivered && !"CANCELLED".equals(order.getStatus())) order.setStatus("RECEIVED");
            else if (!fullyDelivered && !"CANCELLED".equals(order.getStatus())) {
                boolean hasDelivery = false;
                for (Double value : mergedDelivered.values()) {
                    if (value != null && value > 0) {
                        hasDelivery = true;
                        break;
                    }
                }
                if (hasDelivery) order.setStatus("PARTIALLY_RECEIVED");
            }
            order.setReceivedQtyJson(writeQty(mergedReceived));
            order.setDeliveredQtyJson(writeQty(mergedDelivered));
            order.setReceiptsJson(writeReceipts(mergedReceipts));
            if (!mergedReceipts.isEmpty()) order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
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
                .deliveredQtyJson(writeQty(deliveredQty))
                .receiptsJson(writeReceipts(request.receipts()))
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
        out.put("deliveredQty", readQty(order.getDeliveredQtyJson()).isEmpty()
                ? readQty(order.getReceivedQtyJson()) : readQty(order.getDeliveredQtyJson()));
        out.put("receipts", readReceipts(order.getReceiptsJson()));
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

    private void validateReceipts(List<ReceiptRequest> receipts, Map<String, Double> orderedByProduct) {
        if (receipts == null) return;
        for (ReceiptRequest receipt : receipts) {
            if (receipt == null || receipt.receiptId() == null || receipt.receiptId().isBlank()
                    || !Double.isFinite(receipt.invoiceAmount()) || receipt.invoiceAmount() < 0
                    || !Double.isFinite(receipt.freightAmount()) || receipt.freightAmount() < 0
                    || !Double.isFinite(receipt.discountAmount()) || receipt.discountAmount() < 0
                    || (receipt.attachmentPath() != null && !receipt.attachmentPath().isBlank()
                    && !receipt.attachmentPath().startsWith("https://")
                    && !receipt.attachmentPath().startsWith("http://"))
                    || receipt.items() == null || receipt.items().isEmpty()) {
                throw BusinessException.badRequest("Invalid supplier receipt or invoice details");
            }
            for (ReceiptItemRequest item : receipt.items()) {
                Double ordered = orderedByProduct.get(item.productId());
                if (ordered == null || !Double.isFinite(item.orderedQty()) || item.orderedQty() <= 0
                        || !Double.isFinite(item.deliveredQty()) || item.deliveredQty() <= 0
                        || !Double.isFinite(item.acceptedQty()) || item.acceptedQty() < 0
                        || !Double.isFinite(item.damagedQty()) || item.damagedQty() < 0
                        || !Double.isFinite(item.unitCost()) || item.unitCost() < 0
                        || !Double.isFinite(item.orderedUnitCost()) || item.orderedUnitCost() < 0
                        || item.deliveredQty() > item.orderedQty() + 0.000001
                        || item.acceptedQty() > item.deliveredQty() + 0.000001
                        || (item.damagedQty() > 0 && (item.issueNote() == null
                        || item.issueNote().trim().length() < 3))
                        || Math.abs(item.deliveredQty() - item.acceptedQty() - item.damagedQty()) > 0.000001) {
                    throw BusinessException.badRequest("Invalid supplier receipt item");
                }
            }
        }
    }

    private String writeReceipts(List<ReceiptRequest> receipts) {
        try { return mapper.writeValueAsString(receipts == null ? List.of() : receipts); }
        catch (JsonProcessingException e) { throw BusinessException.badRequest("Invalid supplier receipt details"); }
    }

    private List<ReceiptRequest> readReceiptRecords(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private List<ReceiptRequest> mergeReceipts(List<ReceiptRequest> current, List<ReceiptRequest> incoming) {
        Map<String, ReceiptRequest> unique = new java.util.LinkedHashMap<>();
        current.forEach(receipt -> unique.put(receipt.receiptId(), receipt));
        if (incoming != null) incoming.forEach(receipt -> unique.putIfAbsent(receipt.receiptId(), receipt));
        return List.copyOf(unique.values());
    }

    private Map<String, Double> receiptQuantities(List<ReceiptRequest> receipts, boolean accepted) {
        Map<String, Double> totals = new HashMap<>();
        if (receipts == null) return totals;
        for (ReceiptRequest receipt : receipts) {
            if (receipt.items() == null) continue;
            for (ReceiptItemRequest item : receipt.items()) {
                totals.merge(item.productId(), accepted ? item.acceptedQty() : item.deliveredQty(), Double::sum);
            }
        }
        return totals;
    }

    private Map<String, Double> mergeQuantities(Map<String, Double> first, Map<String, Double> second) {
        Map<String, Double> result = new HashMap<>(first == null ? Map.of() : first);
        if (second != null) second.forEach((key, value) -> result.merge(key, value,
                (oldValue, newValue) -> Math.max(oldValue, newValue)));
        return result;
    }

    private List<Map<String, Object>> readReceipts(String value) {
        return readReceiptRecords(value).stream().map(receipt -> mapper.convertValue(receipt,
                new TypeReference<Map<String, Object>>() {})).toList();
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
