package com.haalkhata.feature.purchase.infrastructure;

import com.haalkhata.feature.purchase.domain.Purchase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, String> {

    List<Purchase> findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId);

    Optional<Purchase> findByPurchaseIdAndShopIdAndIsDeletedFalse(String purchaseId, String shopId);

    List<Purchase> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<Purchase> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
