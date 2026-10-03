package com.haalkhata.feature.sale.returning.infrastructure;

import com.haalkhata.feature.sale.returning.domain.SaleReturn;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, String> {
    Optional<SaleReturn> findByReturnIdAndShopIdAndIsDeletedFalse(String returnId, String shopId);
    Optional<SaleReturn> findByReturnIdAndShopIdAndBranchIdAndIsDeletedFalse(String returnId, String shopId, String branchId);
    List<SaleReturn> findByShopIdAndIsDeletedFalseAndSaleId(String shopId, String saleId);
    List<SaleReturn> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);
    List<SaleReturn> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);
    List<SaleReturn> findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, String branchId, LocalDateTime after);
    List<SaleReturn> findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, String branchId, LocalDateTime after);
    List<SaleReturn> findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId);
}
