package com.haalkhata.feature.guardian.infrastructure;

import com.haalkhata.feature.guardian.domain.GuardianAuditEvent;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianAuditEventRepository extends JpaRepository<GuardianAuditEvent, String> {
    List<GuardianAuditEvent> findTop100ByShopIdOrderByCreatedAtDesc(String shopId);
    List<GuardianAuditEvent> findTop100ByShopIdAndBranchIdOrderByCreatedAtDesc(String shopId, String branchId);
    List<GuardianAuditEvent> findByShopIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(String shopId, LocalDateTime after);
    List<GuardianAuditEvent> findByShopIdAndBranchIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(String shopId, String branchId, LocalDateTime after);
    void deleteByShopId(String shopId);
}
