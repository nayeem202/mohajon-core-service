package com.haalkhata.feature.guardian.infrastructure;

import com.haalkhata.feature.guardian.domain.GuardianAuditEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianAuditEventRepository extends JpaRepository<GuardianAuditEvent, String> {
    List<GuardianAuditEvent> findTop100ByShopIdOrderByCreatedAtDesc(String shopId);
    void deleteByShopId(String shopId);
}
