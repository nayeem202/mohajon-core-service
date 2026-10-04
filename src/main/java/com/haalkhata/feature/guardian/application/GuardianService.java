package com.haalkhata.feature.guardian.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.expense.infrastructure.ExpenseRepository;
import com.haalkhata.feature.guardian.domain.GuardianAuditEvent;
import com.haalkhata.feature.guardian.infrastructure.GuardianAuditEventRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

@Service @RequiredArgsConstructor
public class GuardianService {
    private final GuardianAuditEventRepository repository;
    private final ExpenseRepository expenseRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(CurrentUser user, String action, String entityType, String entityId, Double amount, Map<String, Object> metadata) {
        String json = null;
        if (metadata != null && !metadata.isEmpty()) {
            try { json = objectMapper.writeValueAsString(metadata); } catch (JsonProcessingException ignored) { json = metadata.toString(); }
        }
        repository.save(GuardianAuditEvent.builder().eventId(Ids.uuid()).shopId(user.getShopId()).branchId(user.getBranchId()).actorUserId(user.getUserId())
                .action(action).entityType(entityType).entityId(entityId).amount(amount).metadata(json).createdAt(LocalDateTime.now()).build());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(CurrentUser user) {
        List<Map<String, Object>> events = repository.findTop100ByShopIdAndBranchIdOrderByCreatedAtDesc(user.getShopId(), user.getBranchId()).stream().map(this::toMap).toList();
        List<Map<String, Object>> alerts = new ArrayList<>();
        events.stream().filter(e -> "DELETE".equals(e.get("action"))).limit(5).forEach(e -> alerts.add(alert("deletion", "A record was deleted", "Review this deletion in the audit timeline.", e)));
        events.stream().filter(e -> "SALE_CREATED".equals(e.get("action")) && ((Number) e.getOrDefault("amount", 0)).doubleValue() >= 10000).limit(5)
                .forEach(e -> alerts.add(alert("large_sale", "Large sale recorded", "Confirm the payment and stock movement for this sale.", e)));
        expenseRepository.findByShopIdAndBranchIdAndIsDeletedFalseOrderByDateDesc(user.getShopId(), user.getBranchId()).stream().filter(e -> e.getAmount() >= 10000).limit(5).forEach(e -> {
            Map<String, Object> a = new LinkedHashMap<>(); a.put("type", "large_expense"); a.put("title", "Large expense needs review"); a.put("body", e.getDescription()); a.put("amount", e.getAmount()); alerts.add(a);
        });
        Map<String, Object> response = new LinkedHashMap<>(); response.put("riskScore", Math.min(100, alerts.size() * 12)); response.put("alerts", alerts.stream().limit(12).toList()); response.put("events", events); return response;
    }

    /**
     * Cursor pagination keeps the audit archive off the normal sync path.
     * The cursor carries both timestamp and ID, so rows written during a
     * scroll cannot create a duplicate or a gap at a timestamp boundary.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> timeline(CurrentUser user, String cursor, int requestedLimit) {
        int limit = Math.max(10, Math.min(requestedLimit, 50));
        Cursor before = parseCursor(cursor);
        List<GuardianAuditEvent> fetched = repository.findTimelinePage(
                user.getShopId(), user.getBranchId(), before.at(), before.id(), PageRequest.of(0, limit + 1));
        boolean hasMore = fetched.size() > limit;
        List<GuardianAuditEvent> page = hasMore ? fetched.subList(0, limit) : fetched;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("events", page.stream().map(this::timelineMap).toList());
        response.put("hasMore", hasMore);
        response.put("nextCursor", hasMore && !page.isEmpty() ? cursorFor(page.get(page.size() - 1)) : null);
        return response;
    }

    private Cursor parseCursor(String raw) {
        if (raw == null || raw.isBlank()) return new Cursor(null, "");
        int divider = raw.lastIndexOf('|');
        if (divider <= 0 || divider == raw.length() - 1) {
            throw BusinessException.badRequest("Invalid audit timeline cursor");
        }
        try {
            return new Cursor(LocalDateTime.parse(raw.substring(0, divider)), raw.substring(divider + 1));
        } catch (DateTimeParseException error) {
            throw BusinessException.badRequest("Invalid audit timeline cursor");
        }
    }

    private String cursorFor(GuardianAuditEvent event) {
        return event.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "|" + event.getEventId();
    }

    private Map<String, Object> timelineMap(GuardianAuditEvent event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", event.getEventId());
        result.put("action", event.getAction());
        result.put("entityType", event.getEntityType());
        result.put("entityId", event.getEntityId());
        result.put("actorId", event.getActorUserId());
        result.put("shopId", event.getShopId());
        result.put("branchId", event.getBranchId());
        result.put("createdAt", event.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("updatedAt", event.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("isDeleted", false);
        try {
            result.put("details", objectMapper.readValue(
                    event.getMetadata() == null ? "{}" : event.getMetadata(), Map.class));
        } catch (JsonProcessingException ignored) {
            result.put("details", Map.of());
        }
        return result;
    }

    private record Cursor(LocalDateTime at, String id) { }

    private Map<String, Object> alert(String type, String title, String body, Map<String, Object> event) {
        Map<String, Object> a = new LinkedHashMap<>(); a.put("type", type); a.put("title", title); a.put("body", body); a.put("eventId", event.get("eventId")); a.put("amount", event.get("amount")); return a;
    }

    private Map<String, Object> toMap(GuardianAuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("eventId", e.getEventId()); m.put("actorUserId", e.getActorUserId()); m.put("action", e.getAction()); m.put("entityType", e.getEntityType()); m.put("entityId", e.getEntityId()); m.put("amount", e.getAmount()); m.put("metadata", e.getMetadata()); m.put("createdAt", e.getCreatedAt()); return m;
    }
}
