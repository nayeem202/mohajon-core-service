package com.haalkhata.feature.purchase.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.purchase.domain.PurchaseOrder;
import com.haalkhata.feature.purchase.domain.PurchaseOrderItem;
import com.haalkhata.feature.purchase.infrastructure.PurchaseOrderItemRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseOrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    public record ItemRequest(String productId, String name, double qty, double unitCost) {
    }

    public record CreateRequest(String orderId, String supplierId, List<ItemRequest> items,
                                String status, String notes, double totalAmount) {
    }

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository itemRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(CurrentUser user) {
        return orderRepository.findByShopIdOrderByCreatedAtDesc(user.getShopId())
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
        for (ItemRequest item : request.items()) {
            if (item.productId() == null || item.productId().isBlank()
                    || item.qty() <= 0 || item.unitCost() < 0) {
                throw BusinessException.badRequest("Invalid purchase order item");
            }
        }
        var existing = orderRepository.findByOrderIdAndShopId(request.orderId(), user.getShopId());
        if (existing.isPresent()) return json(existing.get());
        var now = LocalDateTime.now();
        var order = orderRepository.save(PurchaseOrder.builder()
                .orderId(request.orderId().trim())
                .shopId(user.getShopId())
                .supplierId(blankToNull(request.supplierId()))
                .status(request.status() == null || request.status().isBlank() ? "DRAFT" : request.status())
                .totalAmount(Math.max(0, request.totalAmount()))
                .notes(trim(request.notes(), 500))
                .createdAt(now)
                .updatedAt(now)
                .build());
        itemRepository.saveAll(request.items().stream().map(item -> PurchaseOrderItem.builder()
                .itemId(java.util.UUID.randomUUID().toString())
                .orderId(order.getOrderId())
                .productId(item.productId().trim())
                .name(trim(item.name(), 200))
                .qty(item.qty())
                .unitCost(item.unitCost())
                .build()).toList());
        return json(order);
    }

    private Map<String, Object> json(PurchaseOrder order) {
        return Map.of(
                "orderId", order.getOrderId(),
                "supplierId", order.getSupplierId() == null ? "" : order.getSupplierId(),
                "status", order.getStatus(),
                "totalAmount", order.getTotalAmount(),
                "notes", order.getNotes() == null ? "" : order.getNotes(),
                "createdAt", order.getCreatedAt(),
                "updatedAt", order.getUpdatedAt(),
                "items", itemRepository.findByOrderId(order.getOrderId()).stream().map(item -> Map.of(
                        "productId", item.getProductId(),
                        "name", item.getName(),
                        "qty", item.getQty(),
                        "unitCost", item.getUnitCost())).toList());
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
