package com.haalkhata.feature.purchase.infrastructure;

import com.haalkhata.feature.purchase.domain.Supplier;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, String> {

    List<Supplier> findByShopIdAndIsDeletedFalseOrderByNameAsc(String shopId);

    Optional<Supplier> findBySupplierIdAndShopIdAndIsDeletedFalse(String supplierId, String shopId);

    List<Supplier> findByShopIdAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    List<Supplier> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
