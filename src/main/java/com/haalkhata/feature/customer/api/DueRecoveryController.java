package com.haalkhata.feature.customer.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.customer.application.DueRecoveryService;
import com.haalkhata.feature.customer.application.DueRecoveryService.FollowUpRequest;
import com.haalkhata.feature.customer.application.DueRecoveryService.PlanRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/due-recovery")
@RequiredArgsConstructor
public class DueRecoveryController {

    private final DueRecoveryService dueRecoveryService;

    @GetMapping
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser user,
                                   @PathVariable String customerId) {
        return dueRecoveryService.get(user, customerId);
    }

    @PutMapping
    public Map<String, Object> save(@AuthenticationPrincipal CurrentUser user,
                                    @PathVariable String customerId,
                                    @Valid @RequestBody PlanRequest request) {
        return dueRecoveryService.savePlan(user, customerId, request);
    }

    @PostMapping("/follow-ups")
    public Map<String, Object> followUp(@AuthenticationPrincipal CurrentUser user,
                                        @PathVariable String customerId,
                                        @Valid @RequestBody FollowUpRequest request) {
        return dueRecoveryService.addFollowUp(user, customerId, request);
    }
}
