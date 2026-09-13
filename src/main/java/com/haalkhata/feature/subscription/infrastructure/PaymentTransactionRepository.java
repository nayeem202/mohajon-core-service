package com.haalkhata.feature.subscription.infrastructure;

import com.haalkhata.feature.subscription.domain.PaymentTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction> findByInternalTransactionId(String internalTransactionId);

    Optional<PaymentTransaction> findByPaymentIdAndShopId(String paymentId, String shopId);

    Optional<PaymentTransaction> findByGatewayPaymentId(String gatewayPaymentId);

    List<PaymentTransaction> findByShopIdOrderByCreatedAtDesc(String shopId);
}