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
@Table(name = "support_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportMessage {
    @Id
    @Column(name = "message_id", length = 36)
    private String messageId;
    @Column(name = "ticket_id", nullable = false, length = 36)
    private String ticketId;
    @Column(name = "sender_user_id", nullable = false, length = 60)
    private String senderUserId;
    @Column(name = "sender_type", nullable = false, length = 16)
    private String senderType;
    @Column(columnDefinition = "text")
    private String body;
    @Column(name = "image_url", length = 500)
    private String imageUrl;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
