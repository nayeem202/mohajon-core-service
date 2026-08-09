package com.haalkhata.feature.vendor.infrastructure;

import com.haalkhata.feature.vendor.domain.Vendor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, String> {

    List<Vendor> findByShopIdAndIsDeletedFalseOrderByCompanyNameAsc(String shopId);

    Optional<Vendor> findByVendorIdAndShopIdAndIsDeletedFalse(String vendorId, String shopId);

    void deleteByShopId(String shopId);
}
