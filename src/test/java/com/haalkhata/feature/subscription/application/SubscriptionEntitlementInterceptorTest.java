package com.haalkhata.feature.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubscriptionEntitlementInterceptorTest {

    private final SubscriptionEntitlementInterceptor interceptor =
            new SubscriptionEntitlementInterceptor(null, null);

    @Test
    void businessFeaturesRequireBusinessTierForReadsAndWrites() {
        assertThat(interceptor.requiredTier("/api/v1/branches", "GET"))
                .isEqualTo(SubscriptionService.Tier.BUSINESS);
        assertThat(interceptor.requiredTier("/api/v1/branches", "POST"))
                .isEqualTo(SubscriptionService.Tier.BUSINESS);
        assertThat(interceptor.requiredTier("/api/v1/branches/branch-1/members/user-1", "POST"))
                .isEqualTo(SubscriptionService.Tier.BUSINESS);
        assertThat(interceptor.requiredTier("/api/v1/users", "GET"))
                .isEqualTo(SubscriptionService.Tier.BUSINESS);
        assertThat(interceptor.requiredTier("/api/v1/users/user-1", "PATCH"))
                .isEqualTo(SubscriptionService.Tier.BUSINESS);
        assertThat(interceptor.requiredTier("/api/v1/roles", "GET"))
                .isEqualTo(SubscriptionService.Tier.BUSINESS);
        assertThat(interceptor.requiredTier("/api/v1/business-guardian/timeline", "GET"))
                .isEqualTo(SubscriptionService.Tier.BUSINESS);
    }

    @Test
    void publicAndPersonalAccountReadsKeepTheirExistingAccess() {
        assertThat(interceptor.requiredTier("/api/v1/users/me", "GET")).isNull();
        assertThat(interceptor.requiredTier("/api/v1/users/me/menus", "GET")).isNull();
        assertThat(interceptor.requiredTier("/api/v1/menus", "GET")).isNull();
        assertThat(interceptor.requiredTier("/api/v1/business-guardian", "GET")).isNull();
        assertThat(interceptor.requiredTier("/api/v1/purchases", "GET")).isNull();
    }

    @Test
    void reportExportsRemainProFeatures() {
        assertThat(interceptor.requiredTier("/api/v1/reports/sales/export", "GET"))
                .isEqualTo(SubscriptionService.Tier.PRO);
    }
}
