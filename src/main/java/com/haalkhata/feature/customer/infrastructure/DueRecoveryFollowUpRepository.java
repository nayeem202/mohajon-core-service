package com.haalkhata.feature.customer.infrastructure;

import com.haalkhata.feature.customer.domain.DueRecoveryFollowUp;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DueRecoveryFollowUpRepository extends JpaRepository<DueRecoveryFollowUp, String> {

    List<DueRecoveryFollowUp> findTop50ByShopIdAndCustomerIdOrderByCreatedAtDesc(
            String shopId, String customerId);

    void deleteByShopId(String shopId);
}
