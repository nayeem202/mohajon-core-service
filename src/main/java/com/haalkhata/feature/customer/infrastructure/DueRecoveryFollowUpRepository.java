package com.haalkhata.feature.customer.infrastructure;

import com.haalkhata.feature.customer.domain.DueRecoveryFollowUp;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DueRecoveryFollowUpRepository extends JpaRepository<DueRecoveryFollowUp, String> {

    @Modifying
    @Query("update DueRecoveryFollowUp f set f.customerId = :target, f.updatedAt = :now "
            + "where f.shopId = :shopId and f.customerId = :source")
    int reassignCustomer(@Param("shopId") String shopId, @Param("source") String source,
                         @Param("target") String target, @Param("now") java.time.LocalDateTime now);

    List<DueRecoveryFollowUp> findTop50ByShopIdAndCustomerIdOrderByCreatedAtDesc(
            String shopId, String customerId);

    void deleteByShopId(String shopId);
}
