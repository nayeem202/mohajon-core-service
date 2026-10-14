package com.haalkhata.feature.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.auth.infrastructure.AppUserRepository;
import com.haalkhata.feature.notification.application.NotificationService;
import com.haalkhata.feature.shop.infrastructure.ShopRepository;
import com.haalkhata.feature.subscription.application.SubscriptionService;
import com.haalkhata.feature.subscription.infrastructure.PaymentTransactionRepository;
import com.haalkhata.feature.subscription.infrastructure.SubscriptionRepository;
import com.haalkhata.feature.support.infrastructure.SupportTicketRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformAdminServiceTest {
    @Mock private AppProperties properties;
    @Mock private ShopRepository shops;
    @Mock private SubscriptionRepository subscriptions;
    @Mock private PaymentTransactionRepository payments;
    @Mock private SupportTicketRepository supportTickets;
    @Mock private SubscriptionService subscriptionService;
    @Mock private AppUserRepository users;
    @Mock private NotificationService notifications;
    @InjectMocks private PlatformAdminService service;

    @Test
    void access_onlyAllowsExplicitPlatformAccountIds() {
        when(properties.getPlatformAdminUserIds()).thenReturn("platform-1, platform-2");

        assertThat(service.access(user("platform-2")).get("platformAdmin")).isEqualTo(true);
        assertThat(service.access(user("shop-admin")).get("platformAdmin")).isEqualTo(false);
    }

    @Test
    void overview_rejectsNormalShopAdminEvenWhenTheirRoleIsAdmin() {
        when(properties.getPlatformAdminUserIds()).thenReturn("platform-1");

        assertThatThrownBy(() -> service.overview(user("shop-admin")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Platform admin access required");
    }

    @Test
    void sendPush_requiresAnExpiryTime() {
        when(properties.getPlatformAdminUserIds()).thenReturn("platform-1");

        assertThatThrownBy(() -> service.sendPush(user("platform-1"),
                new PlatformAdminService.PushRequest("ALL", null, "Update", "Details",
                        null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expiry time in the future");
    }

    private CurrentUser user(String userId) {
        return new CurrentUser(userId, userId + "@example.com", "Test Admin", "shop-1", "ADMIN", List.of("ADMIN"));
    }
}
