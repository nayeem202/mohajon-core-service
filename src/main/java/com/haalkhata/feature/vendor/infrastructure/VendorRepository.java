package com.haalkhata.feature.vendor.infrastructure;

import com.haalkhata.feature.vendor.domain.Vendor;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, String> {

    List<Vendor> findByShopIdAndIsDeletedFalseOrderByCompanyNameAsc(String shopId);

    Optional<Vendor> findByVendorIdAndShopIdAndIsDeletedFalse(String vendorId, String shopId);

    List<Vendor> findByShopIdAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<Vendor> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
