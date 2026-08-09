package com.haalkhata.feature.purchase.infrastructure;

import com.haalkhata.feature.purchase.domain.PurchaseItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, String> {

    List<PurchaseItem> findByPurchaseId(String purchaseId);

    void deleteByPurchaseId(String purchaseId);
}
