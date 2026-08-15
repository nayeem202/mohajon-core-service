package com.haalkhata.feature.notification.infrastructure;

import com.haalkhata.feature.notification.domain.NotificationHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, String> {
    List<NotificationHistory> findTop100ByShopIdOrderByCreatedAtDesc(String shopId);
}
