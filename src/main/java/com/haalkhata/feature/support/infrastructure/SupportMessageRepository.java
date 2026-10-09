package com.haalkhata.feature.support.infrastructure;

import com.haalkhata.feature.support.domain.SupportMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, String> {
    List<SupportMessage> findByTicketIdOrderByCreatedAtDesc(String ticketId, Pageable pageable);
}
