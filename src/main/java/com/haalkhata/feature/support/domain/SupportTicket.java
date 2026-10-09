package com.haalkhata.feature.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {
    @Id
    @Column(name = "ticket_id", length = 36)
    private String ticketId;
    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;
    @Column(name = "opened_by_user_id", nullable = false, length = 60)
    private String openedByUserId;
    @Column(name = "shop_name", nullable = false, length = 150)
    private String shopName;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "last_message_preview", nullable = false, length = 300)
    private String lastMessagePreview;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
