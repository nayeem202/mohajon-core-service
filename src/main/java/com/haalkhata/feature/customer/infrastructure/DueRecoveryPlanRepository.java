package com.haalkhata.feature.customer.infrastructure;

import com.haalkhata.feature.customer.domain.DueRecoveryPlan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DueRecoveryPlanRepository extends JpaRepository<DueRecoveryPlan, String> {


    Optional<DueRecoveryPlan> findByShopIdAndCustomerId(String shopId, String customerId);

    void deleteByShopId(String shopId);
}
