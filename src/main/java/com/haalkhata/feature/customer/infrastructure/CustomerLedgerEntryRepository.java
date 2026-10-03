package com.haalkhata.feature.customer.infrastructure;

import com.haalkhata.feature.customer.domain.CustomerLedgerEntry;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerLedgerEntryRepository extends JpaRepository<CustomerLedgerEntry, String> {

    @Modifying
    @Query("update CustomerLedgerEntry e set e.customerId = :target, e.updatedAt = :now "
            + "where e.shopId = :shopId and e.customerId = :source and e.isDeleted = false")
    int reassignCustomer(@Param("shopId") String shopId, @Param("source") String source,
                         @Param("target") String target, @Param("now") LocalDateTime now);

    List<CustomerLedgerEntry> findByShopIdAndCustomerIdAndIsDeletedFalseOrderByCreatedAtAsc(
            String shopId, String customerId);

    List<CustomerLedgerEntry> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<CustomerLedgerEntry> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
