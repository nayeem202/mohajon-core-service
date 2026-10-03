package com.haalkhata.feature.customer.application;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.core.util.Ids;
import com.haalkhata.feature.customer.domain.DueRecoveryFollowUp;
import com.haalkhata.feature.customer.domain.DueRecoveryPlan;
import com.haalkhata.feature.customer.infrastructure.CustomerRepository;
import com.haalkhata.feature.customer.infrastructure.DueRecoveryFollowUpRepository;
import com.haalkhata.feature.customer.infrastructure.DueRecoveryPlanRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DueRecoveryService {

    private final CustomerRepository customerRepository;
    private final DueRecoveryPlanRepository planRepository;
    private final DueRecoveryFollowUpRepository followUpRepository;

    public record PlanRequest(LocalDate promisedPaymentDate, LocalDateTime snoozedUntil) {
    }

    public record FollowUpRequest(String id, String channel, String note, String status,
                                  String failureReason, String providerMessageId) {
    }

    public record StatusRequest(String status, String failureReason, String providerMessageId) {
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(CurrentUser user, String customerId) {
        requireCustomer(user, customerId);
        var plan = planRepository.findByShopIdAndCustomerId(user.getShopId(), customerId)
                .orElse(null);
        var followUps = followUpRepository
                .findTop50ByShopIdAndCustomerIdOrderByCreatedAtDesc(user.getShopId(), customerId);
        return response(plan, followUps);
    }

    @Transactional
    public Map<String, Object> savePlan(CurrentUser user, String customerId, PlanRequest request) {
        requireCustomer(user, customerId);
        var now = LocalDateTime.now();
        var plan = planRepository.findByShopIdAndCustomerId(user.getShopId(), customerId)
                .orElseGet(() -> DueRecoveryPlan.builder()
                        .id(Ids.uuid())
                        .shopId(user.getShopId())
                        .customerId(customerId)
                        .createdAt(now)
                        .build());
        plan.setPromisedPaymentDate(request == null ? null : request.promisedPaymentDate());
        plan.setSnoozedUntil(request == null ? null : request.snoozedUntil());
        plan.setUpdatedAt(now);
        planRepository.save(plan);
        return response(plan, followUpRepository
                .findTop50ByShopIdAndCustomerIdOrderByCreatedAtDesc(user.getShopId(), customerId));
    }

    @Transactional
    public Map<String, Object> addFollowUp(CurrentUser user, String customerId,
                                           FollowUpRequest request) {
        requireCustomer(user, customerId);
        if (request == null || request.channel() == null || request.channel().isBlank()) {
            throw BusinessException.badRequest("Follow-up channel is required");
        }
        String channel = request.channel().trim().toLowerCase();
        if (!List.of("whatsapp", "sms", "call").contains(channel)) {
            throw BusinessException.badRequest("Unsupported follow-up channel");
        }
        String note = request.note() == null ? null : request.note().trim();
        if (note != null && note.length() > 500) {
            throw BusinessException.badRequest("Follow-up note is too long");
        }
        String status = request.status() == null || request.status().isBlank()
                ? "opened" : request.status().trim().toLowerCase();
        if (!List.of("queued", "sent", "delivered", "opened", "failed").contains(status)) {
            throw BusinessException.badRequest("Unsupported follow-up status");
        }
        String followUpId = request.id() == null || request.id().isBlank()
                ? Ids.uuid() : request.id().trim();
        var existing = followUpRepository.findById(followUpId);
        if (existing.isPresent()) {
            if (!user.getShopId().equals(existing.get().getShopId())
                    || !customerId.equals(existing.get().getCustomerId())) {
                throw BusinessException.forbidden("Follow-up belongs to another shop");
            }
            var current = existing.get();
            if (!channel.equals(current.getChannel())) {
                throw BusinessException.badRequest("Follow-up channel cannot be changed");
            }
            String failureReason = trim(request.failureReason(), 500);
            String providerMessageId = trim(request.providerMessageId(), 200);
            String nextNote = note != null && !note.isBlank() ? note : current.getNote();
            boolean changed = !status.equals(current.getStatus())
                    || !java.util.Objects.equals(nextNote, current.getNote())
                    || !java.util.Objects.equals(failureReason, current.getFailureReason())
                    || !java.util.Objects.equals(providerMessageId, current.getProviderMessageId());
            if (!changed) return get(user, customerId);
            current.setStatus(status);
            current.setNote(nextNote);
            current.setFailureReason(failureReason);
            current.setProviderMessageId(providerMessageId);
            current.setStatusChangedAt(LocalDateTime.now());
            current.setUpdatedAt(LocalDateTime.now());
            followUpRepository.save(current);
            return get(user, customerId);
        }
        followUpRepository.save(DueRecoveryFollowUp.builder()
                .followUpId(followUpId)
                .shopId(user.getShopId())
                .customerId(customerId)
                .channel(channel)
                .note(note)
                .status(status)
                .statusChangedAt(LocalDateTime.now())
                .failureReason(trim(request.failureReason(), 500))
                .providerMessageId(trim(request.providerMessageId(), 200))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        return get(user, customerId);
    }

    @Transactional
    public Map<String, Object> updateStatus(CurrentUser user, String customerId,
                                            String followUpId, StatusRequest request) {
        requireCustomer(user, customerId);
        String status = request == null || request.status() == null
                ? "" : request.status().trim().toLowerCase();
        if (!List.of("queued", "sent", "delivered", "opened", "failed").contains(status)) {
            throw BusinessException.badRequest("Unsupported follow-up status");
        }
        var followUp = followUpRepository.findById(followUpId)
                .filter(item -> user.getShopId().equals(item.getShopId())
                        && customerId.equals(item.getCustomerId()))
                .orElseThrow(() -> BusinessException.notFound("Follow-up not found"));
        followUp.setStatus(status);
        followUp.setFailureReason(trim(request == null ? null : request.failureReason(), 500));
        followUp.setProviderMessageId(trim(request == null ? null : request.providerMessageId(), 200));
        followUp.setStatusChangedAt(LocalDateTime.now());
        followUp.setUpdatedAt(LocalDateTime.now());
        followUpRepository.save(followUp);
        return get(user, customerId);
    }

    private void requireCustomer(CurrentUser user, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw BusinessException.badRequest("Customer ID is required");
        }
        customerRepository.findByCustomerIdAndShopIdAndIsDeletedFalse(customerId, user.getShopId())
                .orElseThrow(() -> BusinessException.notFound("Customer not found"));
    }

    private Map<String, Object> response(DueRecoveryPlan plan, List<DueRecoveryFollowUp> followUps) {
        Map<String, Object> result = new HashMap<>();
        if (plan != null) {
            result.put("promisedPaymentDate", plan.getPromisedPaymentDate());
            result.put("snoozedUntil", plan.getSnoozedUntil());
            result.put("updatedAt", plan.getUpdatedAt());
        } else {
            result.put("promisedPaymentDate", null);
            result.put("snoozedUntil", null);
        }
        result.put("followUps", followUps.stream().map(item -> {
            Map<String, Object> json = new HashMap<>();
            json.put("id", item.getFollowUpId());
            json.put("channel", item.getChannel());
            json.put("note", item.getNote() == null ? "" : item.getNote());
            json.put("status", item.getStatus());
            json.put("statusChangedAt", item.getStatusChangedAt());
            json.put("failureReason", item.getFailureReason() == null ? "" : item.getFailureReason());
            json.put("providerMessageId", item.getProviderMessageId() == null ? "" : item.getProviderMessageId());
            json.put("createdAt", item.getCreatedAt());
            json.put("updatedAt", item.getUpdatedAt());
            return json;
        }).toList());
        return result;
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(max, trimmed.length()));
    }
}
