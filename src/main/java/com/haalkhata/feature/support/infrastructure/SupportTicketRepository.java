package com.haalkhata.feature.support.infrastructure;

import com.haalkhata.feature.support.domain.SupportTicket;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, String> {
    List<SupportTicket> findByShopIdOrderByUpdatedAtDesc(String shopId, Pageable pageable);
    List<SupportTicket> findAllByOrderByUpdatedAtDesc(Pageable pageable);
    long countByStatus(String status);
}
