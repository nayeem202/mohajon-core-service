package com.haalkhata.feature.product.adjustment.infrastructure;
import com.haalkhata.feature.product.adjustment.domain.StockAdjustment;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment,String> {
    List<StockAdjustment> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);
    List<StockAdjustment> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);
    List<StockAdjustment> findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, String branchId, LocalDateTime after);
    List<StockAdjustment> findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, String branchId, LocalDateTime after);
}
