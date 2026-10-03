package com.haalkhata.feature.purchase.infrastructure;

import com.haalkhata.feature.purchase.domain.Purchase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, String> {

    List<Purchase> findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId);
    List<Purchase> findByShopIdAndBranchIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId, String branchId);

    Optional<Purchase> findByPurchaseIdAndShopIdAndIsDeletedFalse(String purchaseId, String shopId);
    Optional<Purchase> findByPurchaseIdAndShopIdAndBranchIdAndIsDeletedFalse(String purchaseId, String shopId, String branchId);

    List<Purchase> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<Purchase> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);
    List<Purchase> findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, String branchId, LocalDateTime after);
    List<Purchase> findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, String branchId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
