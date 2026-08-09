package com.haalkhata.feature.payment.infrastructure;

import com.haalkhata.feature.payment.domain.Payment;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    List<Payment> findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId);

    List<Payment> findByShopIdAndCustomerIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId, String customerId);

    List<Payment> findByShopIdAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    List<Payment> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
