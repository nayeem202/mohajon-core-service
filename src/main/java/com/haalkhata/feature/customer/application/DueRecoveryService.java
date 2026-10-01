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

    public record FollowUpRequest(String id, String channel, String note) {
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
        String followUpId = request.id() == null || request.id().isBlank()
                ? Ids.uuid() : request.id().trim();
        var existing = followUpRepository.findById(followUpId);
        if (existing.isPresent()) {
            if (!user.getShopId().equals(existing.get().getShopId())
                    || !customerId.equals(existing.get().getCustomerId())) {
                throw BusinessException.forbidden("Follow-up belongs to another shop");
            }
            return get(user, customerId);
        }
        followUpRepository.save(DueRecoveryFollowUp.builder()
                .followUpId(followUpId)
                .shopId(user.getShopId())
                .customerId(customerId)
                .channel(channel)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build());
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
        result.put("followUps", followUps.stream().map(item -> Map.of(
                "id", item.getFollowUpId(),
                "channel", item.getChannel(),
                "note", item.getNote() == null ? "" : item.getNote(),
                "createdAt", item.getCreatedAt())).toList());
        return result;
    }
}
