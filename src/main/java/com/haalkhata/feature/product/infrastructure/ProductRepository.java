package com.haalkhata.feature.product.infrastructure;

import com.haalkhata.feature.product.domain.Product;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findByShopIdAndIsDeletedFalseOrderByNameEnAsc(String shopId);

    Optional<Product> findByProductIdAndShopIdAndIsDeletedFalse(String productId, String shopId);

    List<Product> findByShopIdAndIsDeletedFalseAndStockQtyLessThanEqual(String shopId, int threshold);

    List<Product> findByShopIdAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    List<Product> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
