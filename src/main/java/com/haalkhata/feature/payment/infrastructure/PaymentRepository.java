package com.haalkhata.feature.payment.infrastructure;

import com.haalkhata.feature.payment.domain.Payment;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    @Modifying
    @Query("update Payment p set p.customerId = :target, p.updatedAt = :now "
            + "where p.shopId = :shopId and p.customerId = :source and p.isDeleted = false")
    int reassignCustomer(@Param("shopId") String shopId, @Param("source") String source,
                         @Param("target") String target, @Param("now") LocalDateTime now);

    List<Payment> findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId);

    List<Payment> findByShopIdAndCustomerIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId, String customerId);

    List<Payment> findBySaleIdAndShopIdAndIsDeletedFalse(String saleId, String shopId);
    List<Payment> findBySaleIdAndShopIdAndBranchIdAndIsDeletedFalse(String saleId, String shopId, String branchId);

    List<Payment> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<Payment> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);
    List<Payment> findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, String branchId, LocalDateTime after);
    List<Payment> findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, String branchId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
