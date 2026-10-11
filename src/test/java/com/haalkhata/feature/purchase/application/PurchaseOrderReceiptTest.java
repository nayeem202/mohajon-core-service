package com.haalkhata.feature.purchase.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.purchase.domain.PurchaseOrder;
import com.haalkhata.feature.purchase.domain.PurchaseOrderItem;
import com.haalkhata.feature.purchase.infrastructure.PurchaseOrderItemRepository;
import com.haalkhata.feature.purchase.infrastructure.PurchaseOrderRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderReceiptTest {

    @Mock private PurchaseOrderRepository orders;
    @Mock private PurchaseOrderItemRepository items;

    @Test
    void create_persists_received_and_damaged_quantities_with_invoice_reconciliation() {
        PurchaseOrderService service = new PurchaseOrderService(orders, items, new ObjectMapper());
        CurrentUser owner = new CurrentUser(
                "owner-1", "owner@example.com", "Owner", "shop-1", "OWNER", List.of("OWNER"));
        var receiptLine = new PurchaseOrderService.ReceiptItemRequest(
                "rice", 10, 9, 8, 1, 105, 100, "One bag damaged");
        var receipt = new PurchaseOrderService.ReceiptRequest(
                "receipt-1", "2026-10-09T00:00:00", List.of(receiptLine),
                820, "INV-88", 20, 0, "https://files.example/receipt.jpg", "One item damaged");
        var request = new PurchaseOrderService.CreateRequest(
                "order-1", "supplier-1",
                List.of(new PurchaseOrderService.ItemRequest("rice", "Rice", 10, 100, "", null)),
                "PARTIALLY_RECEIVED", "", 1000,
                Map.of("rice", 8.0), Map.of("rice", 9.0), List.of(receipt),
                false, "2026-10-09T00:00:00", "2026-10-08T00:00:00");
        when(orders.findByOrderIdAndShopIdAndBranchId("order-1", "shop-1", "shop-1-main"))
                .thenReturn(Optional.empty());
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(call -> call.getArgument(0));
        when(items.findByOrderId("order-1")).thenReturn(List.of(PurchaseOrderItem.builder()
                .itemId("item-1").orderId("order-1").productId("rice").name("Rice")
                .qty(10).unitCost(100).build()));

        Map<String, Object> result = service.create(owner, request);

        assertThat(result.get("receivedQty")).isEqualTo(Map.of("rice", 8.0));
        assertThat(result.get("deliveredQty")).isEqualTo(Map.of("rice", 9.0));
        assertThat((List<?>) result.get("receipts")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) result.get("receipts")).getFirst()).get("invoiceNumber"))
                .isEqualTo("INV-88");
        verify(orders).save(any(PurchaseOrder.class));
    }
}
