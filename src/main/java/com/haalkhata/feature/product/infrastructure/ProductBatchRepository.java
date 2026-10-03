package com.haalkhata.feature.product.infrastructure;

import com.haalkhata.feature.product.domain.ProductBatch;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, String> {
    List<ProductBatch> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);
    List<ProductBatch> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);
    List<ProductBatch> findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, String branchId, LocalDateTime after);
    List<ProductBatch> findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, String branchId, LocalDateTime after);
    List<ProductBatch> findByShopIdAndBranchIdAndProductIdAndIsDeletedFalseOrderByExpiryDateAsc(String shopId, String branchId, String productId);
    Optional<ProductBatch> findByBatchIdAndShopId(String batchId, String shopId);

    @Modifying
    @Query("update ProductBatch b set b.quantity = b.quantity - :qty, b.updatedAt = :now "
            + "where b.batchId = :batchId and b.shopId = :shopId and b.branchId = :branchId and b.isDeleted = false and b.quantity >= :qty")
    int decrement(@Param("batchId") String batchId, @Param("shopId") String shopId, @Param("branchId") String branchId,
                  @Param("qty") int qty, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update ProductBatch b set b.quantity = b.quantity + :qty, b.updatedAt = :now "
            + "where b.batchId = :batchId and b.shopId = :shopId and b.branchId = :branchId and b.isDeleted = false")
    int increment(@Param("batchId") String batchId, @Param("shopId") String shopId, @Param("branchId") String branchId,
                  @Param("qty") int qty, @Param("now") LocalDateTime now);
}
