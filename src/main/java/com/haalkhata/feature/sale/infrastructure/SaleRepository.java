package com.haalkhata.feature.sale.infrastructure;

import com.haalkhata.feature.sale.domain.Sale;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface SaleRepository extends JpaRepository<Sale, String> {

    @Modifying
    @Query("update Sale s set s.customerId = :target, s.updatedAt = :now "
            + "where s.shopId = :shopId and s.customerId = :source and s.isDeleted = false")
    int reassignCustomer(@Param("shopId") String shopId, @Param("source") String source,
                         @Param("target") String target, @Param("now") LocalDateTime now);

    List<Sale> findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId);

    List<Sale> findByShopIdAndIsDeletedFalseAndCustomerIdOrderByCreatedAtDesc(String shopId, String customerId);

    Optional<Sale> findBySaleIdAndShopIdAndIsDeletedFalse(String saleId, String shopId);
    Optional<Sale> findBySaleIdAndShopIdAndBranchIdAndIsDeletedFalse(String saleId, String shopId, String branchId);

    List<Sale> findByShopIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, LocalDateTime after);

    List<Sale> findByShopIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, LocalDateTime after);
    List<Sale> findByShopIdAndBranchIdAndIsDeletedFalseAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(String shopId, String branchId, LocalDateTime after);
    List<Sale> findByShopIdAndBranchIdAndIsDeletedTrueAndUpdatedAtGreaterThan(String shopId, String branchId, LocalDateTime after);

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

    @Query("select coalesce(sum(s.dueAmount), 0) from Sale s where s.shopId = :shopId "
            + "and s.isDeleted = false and s.customerId = :customerId")
    double sumDueForCustomer(@Param("shopId") String shopId, @Param("customerId") String customerId);

    @Query("select coalesce(sum(s.totalAmount), 0) from Sale s where s.shopId = :shopId "
            + "and s.branchId = :branchId and s.isDeleted = false and s.createdAt >= :from and s.createdAt < :to")
    double sumTotalForBranch(@Param("shopId") String shopId, @Param("branchId") String branchId,
                             @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(s.paidAmount), 0) from Sale s where s.shopId = :shopId "
            + "and s.branchId = :branchId and s.isDeleted = false and s.createdAt >= :from and s.createdAt < :to")
    double sumPaidForBranch(@Param("shopId") String shopId, @Param("branchId") String branchId,
                            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Aggregates only the columns needed by the dashboard; never loads sale entities/items. */
    @Query(value = "select date_trunc(cast(:bucket as text), s.created_at), "
            + "coalesce(sum(s.total_amount), 0), count(*) "
            + "from sales s where s.shop_id = :shopId and s.branch_id = :branchId "
            + "and s.is_deleted = false and s.created_at >= :from and s.created_at < :to "
            + "group by date_trunc(cast(:bucket as text), s.created_at) "
            + "order by date_trunc(cast(:bucket as text), s.created_at)", nativeQuery = true)
    List<Object[]> aggregateSalesTrend(@Param("shopId") String shopId,
                                       @Param("branchId") String branchId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to,
                                       @Param("bucket") String bucket);

    @Query(value = "select coalesce(sum(s.total_amount), 0), count(*) "
            + "from sales s where s.shop_id = :shopId and s.branch_id = :branchId "
            + "and s.is_deleted = false and s.created_at >= :from and s.created_at < :to",
            nativeQuery = true)
    Object[] aggregateSalesTotals(@Param("shopId") String shopId,
                                  @Param("branchId") String branchId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    void deleteByShopId(String shopId);
}
