package com.haalkhata.feature.vendor.infrastructure;

import com.haalkhata.feature.vendor.domain.VendorPayment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPaymentRepository extends JpaRepository<VendorPayment, String> {

    List<VendorPayment> findByShopIdAndVendorIdAndIsDeletedFalseOrderByDateDesc(String shopId, String vendorId);

    void deleteByShopId(String shopId);
}
