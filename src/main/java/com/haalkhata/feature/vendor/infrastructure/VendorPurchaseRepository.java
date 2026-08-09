package com.haalkhata.feature.vendor.infrastructure;

import com.haalkhata.feature.vendor.domain.VendorPayment;
import com.haalkhata.feature.vendor.domain.VendorPurchase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPurchaseRepository extends JpaRepository<VendorPurchase, String> {

    List<VendorPurchase> findByShopIdAndVendorIdAndIsDeletedFalseOrderByDateDesc(String shopId, String vendorId);

    void deleteByShopId(String shopId);
}
