package com.haalkhata.feature.customer.infrastructure;

import com.haalkhata.feature.customer.domain.Customer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    List<Customer> findByShopIdAndIsDeletedFalseOrderByNameAsc(String shopId);

    Optional<Customer> findByCustomerIdAndShopIdAndIsDeletedFalse(String customerId, String shopId);

    List<Customer> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<Customer> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    void deleteByShopId(String shopId);
}
