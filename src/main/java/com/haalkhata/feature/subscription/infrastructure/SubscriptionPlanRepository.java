package com.haalkhata.feature.subscription.infrastructure;

import com.haalkhata.feature.subscription.domain.SubscriptionPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {

    List<SubscriptionPlan> findByIsActiveTrueOrderBySortOrderAsc();
}