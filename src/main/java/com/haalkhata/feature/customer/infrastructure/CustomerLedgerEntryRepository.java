package com.haalkhata.feature.customer.infrastructure;

import com.haalkhata.feature.customer.domain.CustomerLedgerEntry;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerLedgerEntryRepository extends JpaRepository<CustomerLedgerEntry, String> {

    List<CustomerLedgerEntry> findByShopIdAndCustomerIdAndIsDeletedFalseOrderByCreatedAtAsc(
            String shopId, String customerId);

    List<CustomerLedgerEntry> findByShopIdAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<CustomerLedgerEntry> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
