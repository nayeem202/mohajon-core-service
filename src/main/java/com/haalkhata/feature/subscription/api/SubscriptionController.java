package com.haalkhata.feature.subscription.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.subscription.application.SubscriptionService;
import com.haalkhata.feature.subscription.application.SubscriptionService.ConfirmRequest;
import com.haalkhata.feature.subscription.application.SubscriptionService.PurchaseRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public List<Map<String, Object>> plans() {
        return subscriptionService.plans();
    }

    @PostMapping("/purchase")
    public Map<String, Object> purchase(@AuthenticationPrincipal CurrentUser user,
                                        @Valid @RequestBody PurchaseRequest request) {
        return subscriptionService.purchase(user, request);
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@AuthenticationPrincipal CurrentUser user,
                                       @Valid @RequestBody ConfirmRequest request) {
        return subscriptionService.confirm(user, request);
    }

    @GetMapping("/status")
    public Map<String, Object> status(@AuthenticationPrincipal CurrentUser user) {
        return subscriptionService.status(user);
    }

    @GetMapping("/{subscriptionId}")
    public Map<String, Object> details(@AuthenticationPrincipal CurrentUser user,
                                       @PathVariable String subscriptionId) {
        return subscriptionService.details(subscriptionId, user);
    }
}