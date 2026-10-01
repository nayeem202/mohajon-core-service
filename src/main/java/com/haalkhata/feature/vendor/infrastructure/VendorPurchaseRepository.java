package com.haalkhata.feature.vendor.infrastructure;

import com.haalkhata.feature.vendor.domain.VendorPayment;
import com.haalkhata.feature.vendor.domain.VendorPurchase;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPurchaseRepository extends JpaRepository<VendorPurchase, String> {

    List<VendorPurchase> findByShopIdAndVendorIdAndIsDeletedFalseOrderByDateDesc(String shopId, String vendorId);

    List<VendorPurchase> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<VendorPurchase> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
