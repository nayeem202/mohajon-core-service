package com.haalkhata.feature.subscription.application;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * The API is the source of truth for paid feature access. UI checks are kept
 * for a friendly experience, but cannot be used to bypass the plan limits.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionEntitlementInterceptor implements HandlerInterceptor {

    private final AppProperties props;
    private final SubscriptionService subscriptions;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!props.isEnforceSubscription()) {
            return true;
        }
        CurrentUser user = (CurrentUser) request.getAttribute("currentUser");
        if (user == null) {
            return true;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();
        SubscriptionService.Tier required = requiredTier(path, method);
        if (required != null) {
            subscriptions.requireTier(user.getShopId(), required);
        }
        return true;
    }

    private SubscriptionService.Tier requiredTier(String path, String method) {
        // Export is a GET request, but remains a Pro feature.
        if (path.startsWith("/api/v1/reports/") && path.endsWith("/export")) {
            return SubscriptionService.Tier.PRO;
        }
        if (HttpMethod.GET.matches(method)) {
            return null;
        }
        if (path.startsWith("/api/v1/branches/") || path.equals("/api/v1/branches")
                || (path.startsWith("/api/v1/users") && !path.startsWith("/api/v1/users/me"))) {
            return SubscriptionService.Tier.BUSINESS;
        }
        if (path.startsWith("/api/v1/purchases") || path.startsWith("/api/v1/vendors")
                || path.startsWith("/api/v1/investments") || path.startsWith("/api/v1/investors")) {
            return SubscriptionService.Tier.PRO;
        }
        return null;
    }
}
