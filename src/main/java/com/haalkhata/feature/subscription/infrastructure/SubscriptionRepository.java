package com.haalkhata.feature.subscription.infrastructure;

import com.haalkhata.feature.subscription.domain.Subscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    Optional<Subscription> findTopByShopIdAndStatusOrderByEndDateDesc(String shopId, String status);

    Optional<Subscription> findTopByShopIdAndStatusAndPlanCodeOrderByEndDateDesc(
            String shopId, String status, String planCode);

    List<Subscription> findByShopIdOrderByCreatedAtDesc(String shopId);

    Optional<Subscription> findByInternalTransactionId(String internalTransactionId);

    long countByStatus(String status);
}
