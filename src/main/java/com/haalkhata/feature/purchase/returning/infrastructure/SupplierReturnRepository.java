package com.haalkhata.feature.purchase.returning.infrastructure;
import com.haalkhata.feature.purchase.returning.domain.SupplierReturn;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface SupplierReturnRepository extends JpaRepository<SupplierReturn,String> {
    List<SupplierReturn> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);
    List<SupplierReturn> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);
    List<SupplierReturn> findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, String branchId, LocalDateTime after);
    List<SupplierReturn> findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, String branchId, LocalDateTime after);
    List<SupplierReturn> findByShopIdAndBranchIdAndOrderIdAndIsDeletedFalse(String shopId, String branchId, String orderId);
}
