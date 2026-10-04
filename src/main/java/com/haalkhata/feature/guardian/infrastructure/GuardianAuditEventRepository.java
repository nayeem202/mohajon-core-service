package com.haalkhata.feature.guardian.infrastructure;

import com.haalkhata.feature.guardian.domain.GuardianAuditEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface GuardianAuditEventRepository extends JpaRepository<GuardianAuditEvent, String> {
    List<GuardianAuditEvent> findTop100ByShopIdOrderByCreatedAtDesc(String shopId);
    List<GuardianAuditEvent> findTop100ByShopIdAndBranchIdOrderByCreatedAtDesc(String shopId, String branchId);
    List<GuardianAuditEvent> findByShopIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(String shopId, LocalDateTime after);
    List<GuardianAuditEvent> findByShopIdAndBranchIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(String shopId, String branchId, LocalDateTime after);
    @Query("""
            select e from GuardianAuditEvent e
            where e.shopId = :shopId and e.branchId = :branchId
              and (:beforeAt is null or e.createdAt < :beforeAt
                or (e.createdAt = :beforeAt and e.eventId < :beforeId))
            order by e.createdAt desc, e.eventId desc
            """)
    List<GuardianAuditEvent> findTimelinePage(
            @Param("shopId") String shopId,
            @Param("branchId") String branchId,
            @Param("beforeAt") LocalDateTime beforeAt,
            @Param("beforeId") String beforeId,
            Pageable pageable);
    void deleteByShopId(String shopId);
}
