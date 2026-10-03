package com.haalkhata.feature.purchase.infrastructure;

import com.haalkhata.feature.purchase.domain.PurchaseOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {

    Optional<PurchaseOrder> findByOrderIdAndShopId(String orderId, String shopId);
    Optional<PurchaseOrder> findByOrderIdAndShopIdAndBranchId(String orderId, String shopId, String branchId);

    List<PurchaseOrder> findByShopIdOrderByCreatedAtDesc(String shopId);
    List<PurchaseOrder> findByShopIdAndBranchIdOrderByCreatedAtDesc(String shopId, String branchId);

    void deleteByShopId(String shopId);
}
