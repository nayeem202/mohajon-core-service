package com.haalkhata.feature.support.application;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.notification.application.NotificationService;
import com.haalkhata.feature.shop.infrastructure.ShopRepository;
import com.haalkhata.feature.support.domain.SupportMessage;
import com.haalkhata.feature.support.domain.SupportTicket;
import com.haalkhata.feature.support.infrastructure.SupportMessageRepository;
import com.haalkhata.feature.support.infrastructure.SupportTicketRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportInboxService {
    private static final int MAX_PAGE_SIZE = 50;
    private final SupportTicketRepository tickets;
    private final SupportMessageRepository messages;
    private final ShopRepository shops;
    private final NotificationService notifications;
    private final AppProperties properties;

    @Transactional(readOnly = true)
    public Map<String, Object> access(CurrentUser user) {
        return Map.of("supportAdmin", isSupportAdmin(user));
    }

    @Transactional
    public Map<String, Object> createTicket(CurrentUser user, MessageRequest request) {
        requireContent(request);
        var now = LocalDateTime.now();
        var ticket = SupportTicket.builder()
                .ticketId(UUID.randomUUID().toString())
                .shopId(user.getShopId())
                .openedByUserId(user.getUserId())
                .shopName(shops.findById(user.getShopId()).map(shop -> shop.getName())
                        .orElse("Shop " + user.getShopId()))
                .status("OPEN")
                .lastMessagePreview(preview(request))
                .createdAt(now)
                .updatedAt(now)
                .build();
        tickets.save(ticket);
        messages.save(newMessage(ticket.getTicketId(), user.getUserId(), "OWNER", request, now));
        return ticketMap(ticket);
    }

    @Transactional
    public Map<String, Object> addOwnerMessage(CurrentUser user, String ticketId, MessageRequest request) {
        requireContent(request);
        var ticket = requireOwnedTicket(user, ticketId);
        var now = LocalDateTime.now();
        messages.save(newMessage(ticketId, user.getUserId(), "OWNER", request, now));
        ticket.setStatus("OPEN");
        ticket.setLastMessagePreview(preview(request));
        ticket.setUpdatedAt(now);
        return ticketMap(tickets.save(ticket));
    }

    @Transactional
    public Map<String, Object> addAdminMessage(CurrentUser user, String ticketId, MessageRequest request) {
        requireSupportAdmin(user);
        requireContent(request);
        var ticket = tickets.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Support ticket not found."));
        var now = LocalDateTime.now();
        messages.save(newMessage(ticketId, user.getUserId(), "ADMIN", request, now));
        ticket.setStatus("WAITING_FOR_OWNER");
        ticket.setLastMessagePreview(preview(request));
        ticket.setUpdatedAt(now);
        tickets.save(ticket);
        try {
            notifications.sendToUser(ticket.getOpenedByUserId(),
                    "Mohajon Support", "Support replied to your message.",
                    "মহাজন সাপোর্ট", "সাপোর্ট টিম আপনার বার্তার উত্তর দিয়েছে।",
                    Map.of("screen", "support_inbox", "ticketId", ticketId));
        } catch (BusinessException exception) {
            // The conversation is the source of truth. A temporary FCM problem
            // must never discard an already-written support reply.
            log.warn("Support reply saved but push failed for ticket {}: {}", ticketId,
                    exception.getMessage());
        }
        return ticketMap(ticket);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myTickets(CurrentUser user, int page, int size) {
        int pageSize = safeSize(size);
        return ticketPage(tickets.findByShopIdOrderByUpdatedAtDesc(user.getShopId(), page(page, pageSize)), pageSize);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminTickets(CurrentUser user, int page, int size) {
        requireSupportAdmin(user);
        int pageSize = safeSize(size);
        return ticketPage(tickets.findAllByOrderByUpdatedAtDesc(page(page, pageSize)), pageSize);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ticketMessages(CurrentUser user, String ticketId, int page, int size) {
        var ticket = tickets.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Support ticket not found."));
        if (!isSupportAdmin(user) && !ticket.getShopId().equals(user.getShopId())) {
            throw BusinessException.forbidden("This support ticket belongs to another shop.");
        }
        int pageSize = safeSize(size);
        var rows = messages.findByTicketIdOrderByCreatedAtDesc(ticketId, page(page, pageSize));
        boolean hasMore = rows.size() > pageSize;
        var items = rows.subList(0, Math.min(rows.size(), pageSize));
        return Map.of("ticket", ticketMap(ticket), "items", items.stream()
                .map(this::messageMap).toList(), "page", Math.max(page, 0), "hasMore", hasMore);
    }

    private SupportTicket requireOwnedTicket(CurrentUser user, String ticketId) {
        var ticket = tickets.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Support ticket not found."));
        if (!ticket.getShopId().equals(user.getShopId())) {
            throw BusinessException.forbidden("This support ticket belongs to another shop.");
        }
        return ticket;
    }

    private SupportMessage newMessage(String ticketId, String senderId, String senderType,
                                      MessageRequest request, LocalDateTime now) {
        return SupportMessage.builder().messageId(UUID.randomUUID().toString()).ticketId(ticketId)
                .senderUserId(senderId).senderType(senderType).body(clean(request.message()))
                .imageUrl(clean(request.imageUrl())).createdAt(now).build();
    }

    private void requireContent(MessageRequest request) {
        if (request == null) throw BusinessException.badRequest("Message or image is required.");
        var message = clean(request.message());
        var imageUrl = clean(request.imageUrl());
        if (message == null && imageUrl == null) throw BusinessException.badRequest("Message or image is required.");
        if (message != null && message.length() > 2000) throw BusinessException.badRequest("Message is too long.");
        if (imageUrl != null && (!imageUrl.startsWith("/api/v1/files/") || imageUrl.length() > 500)) {
            throw BusinessException.badRequest("Invalid support image.");
        }
    }

    private String preview(MessageRequest request) {
        var message = clean(request.message());
        return message == null ? "Image attached" : message.substring(0, Math.min(300, message.length()));
    }

    private String clean(String value) {
        if (value == null) return null;
        var result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private PageRequest page(int page, int size) {
        // Fetch one probe row so the client knows whether to show "load more"
        // without loading a full conversation history.
        return PageRequest.of(Math.max(page, 0), size + 1);
    }

    private int safeSize(int size) {
        return Math.max(1, Math.min(size <= 0 ? 30 : size, MAX_PAGE_SIZE));
    }

    private Map<String, Object> ticketPage(List<SupportTicket> rows, int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        return Map.of("items", rows.subList(0, Math.min(rows.size(), pageSize)).stream()
                .map(this::ticketMap).toList(), "hasMore", hasMore);
    }

    private Map<String, Object> ticketMap(SupportTicket ticket) {
        return Map.of("ticketId", ticket.getTicketId(), "shopId", ticket.getShopId(),
                "shopName", ticket.getShopName(), "status", ticket.getStatus(),
                "preview", ticket.getLastMessagePreview(), "createdAt", ticket.getCreatedAt().toString(),
                "updatedAt", ticket.getUpdatedAt().toString());
    }

    private Map<String, Object> messageMap(SupportMessage message) {
        return Map.of("messageId", message.getMessageId(), "senderType", message.getSenderType(),
                "body", message.getBody() == null ? "" : message.getBody(),
                "imageUrl", message.getImageUrl() == null ? "" : message.getImageUrl(),
                "createdAt", message.getCreatedAt().toString());
    }

    private boolean isSupportAdmin(CurrentUser user) {
        Set<String> supportIds = java.util.Arrays.stream(properties.getSupportAdminUserIds().split(","))
                .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toSet());
        Set<String> platformIds = java.util.Arrays.stream(properties.getPlatformAdminUserIds().split(","))
                .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toSet());
        // A platform administrator operates the central support inbox as well.
        // Shop-level ADMIN roles are deliberately not included here.
        return supportIds.contains(user.getUserId()) || platformIds.contains(user.getUserId());
    }

    private void requireSupportAdmin(CurrentUser user) {
        if (!isSupportAdmin(user)) throw BusinessException.forbidden("Support admin access required.");
    }

    public record MessageRequest(String message, String imageUrl) {}
}
