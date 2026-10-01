package com.haalkhata.feature.sale.infrastructure;

import com.haalkhata.feature.sale.domain.Sale;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, String> {

    List<Sale> findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId);

    List<Sale> findByShopIdAndIsDeletedFalseAndCustomerIdOrderByCreatedAtDesc(String shopId, String customerId);

    Optional<Sale> findBySaleIdAndShopIdAndIsDeletedFalse(String saleId, String shopId);

    List<Sale> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<Sale> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);

    @Query("select coalesce(sum(s.totalAmount), 0) from Sale s where s.shopId = :shopId "
            + "and s.isDeleted = false and s.createdAt >= :from and s.createdAt < :to")
    double sumTotal(@Param("shopId") String shopId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(s.paidAmount), 0) from Sale s where s.shopId = :shopId "
            + "and s.isDeleted = false and s.createdAt >= :from and s.createdAt < :to")
    double sumPaid(@Param("shopId") String shopId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(s.totalAmount), 0) from Sale s where s.shopId = :shopId "
            + "and s.isDeleted = false and s.createdAt >= :from and s.createdAt < :to and s.customerId = :customerId")
    double sumTotalForCustomer(@Param("shopId") String shopId, @Param("customerId") String customerId,
                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(s.paidAmount), 0) from Sale s where s.shopId = :shopId "
            + "and s.isDeleted = false and s.createdAt >= :from and s.createdAt < :to and s.customerId = :customerId")
    double sumPaidForCustomer(@Param("shopId") String shopId, @Param("customerId") String customerId,
                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    void deleteByShopId(String shopId);
}
