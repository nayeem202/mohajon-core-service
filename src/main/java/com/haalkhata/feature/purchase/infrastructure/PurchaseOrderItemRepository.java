package com.haalkhata.feature.purchase.infrastructure;

import com.haalkhata.feature.purchase.domain.PurchaseOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, String> {

    List<PurchaseOrderItem> findByOrderId(String orderId);

    void deleteByOrderId(String orderId);
}
