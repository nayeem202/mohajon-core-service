package com.haalkhata.feature.product.infrastructure;

import com.haalkhata.feature.product.domain.Product;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findByShopIdAndIsDeletedFalseOrderByNameEnAsc(String shopId);

    List<Product> findByShopIdAndBranchIdAndIsDeletedFalseOrderByNameEnAsc(String shopId, String branchId);

    Optional<Product> findByProductIdAndShopIdAndIsDeletedFalse(String productId, String shopId);

    Optional<Product> findByProductIdAndShopIdAndBranchIdAndIsDeletedFalse(String productId, String shopId, String branchId);

    /** Atomically decrements stock; returns 1 on success, 0 when the product
     * is missing or has insufficient available stock. */
    @Modifying
    @Query("UPDATE Product p SET p.stockQty = p.stockQty - :qty, p.updatedAt = :now " +
            "WHERE p.productId = :productId AND p.shopId = :shopId " +
            "AND p.isDeleted = false AND p.stockQty >= :qty")
    int decrementStock(String productId, String shopId, int qty, LocalDateTime now);

    @Modifying
    @Query("UPDATE Product p SET p.stockQty = p.stockQty - :qty, p.updatedAt = :now " +
            "WHERE p.productId = :productId AND p.shopId = :shopId AND p.branchId = :branchId " +
            "AND p.isDeleted = false AND p.stockQty >= :qty")
    int decrementStock(String productId, String shopId, String branchId, int qty, LocalDateTime now);

    /** Atomically adds stock; returns 1 on success, 0 when the product is
     * missing or belongs to another shop. */
    @Modifying
    @Query("UPDATE Product p SET p.stockQty = p.stockQty + :qty, p.updatedAt = :now " +
            "WHERE p.productId = :productId AND p.shopId = :shopId AND p.isDeleted = false")
    int incrementStock(String productId, String shopId, int qty, LocalDateTime now);

    @Modifying
    @Query("UPDATE Product p SET p.stockQty = p.stockQty + :qty, p.updatedAt = :now " +
            "WHERE p.productId = :productId AND p.shopId = :shopId AND p.branchId = :branchId AND p.isDeleted = false")
    int incrementStock(String productId, String shopId, String branchId, int qty, LocalDateTime now);

    List<Product> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<Product> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    List<Product> findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, String branchId, LocalDateTime after);
    List<Product> findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, String branchId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
