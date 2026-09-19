package com.haalkhata.feature.vendor.infrastructure;

import com.haalkhata.feature.vendor.domain.VendorPayment;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPaymentRepository extends JpaRepository<VendorPayment, String> {

    List<VendorPayment> findByShopIdAndVendorIdAndIsDeletedFalseOrderByDateDesc(String shopId, String vendorId);

    List<VendorPayment> findByShopIdAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<VendorPayment> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
