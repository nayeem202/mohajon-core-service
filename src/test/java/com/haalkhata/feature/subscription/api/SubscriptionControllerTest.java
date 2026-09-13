package com.haalkhata.feature.subscription.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.subscription.application.SubscriptionService;
import com.haalkhata.feature.subscription.application.SubscriptionService.ConfirmRequest;
import com.haalkhata.feature.subscription.application.SubscriptionService.PurchaseRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SubscriptionControllerTest {

    @Test
    void purchase_delegatesToService() {
        SubscriptionService service = Mockito.mock(SubscriptionService.class);
        SubscriptionController controller = new SubscriptionController(service);
        CurrentUser user = new CurrentUser("u1", "u@test.com", "User1", "shop_1", "OWNER", List.of("OWNER"));
        when(service.purchase(user, new PurchaseRequest("MONTHLY")))
                .thenReturn(Map.of("subscriptionId", "sub_1", "amount", "499.00"));

        Map<String, Object> resp = controller.purchase(user, new PurchaseRequest("MONTHLY"));

        assertThat(resp.get("subscriptionId")).isEqualTo("sub_1");
        verify(service).purchase(user, new PurchaseRequest("MONTHLY"));
    }

    @Test
    void confirm_delegatesToService() {
        SubscriptionService service = Mockito.mock(SubscriptionService.class);
        SubscriptionController controller = new SubscriptionController(service);
        CurrentUser user = new CurrentUser("u1", "u@test.com", "User1", "shop_1", "OWNER", List.of("OWNER"));
        when(service.confirm(user, new ConfirmRequest("tx_1")))
                .thenReturn(Map.of("paymentStatus", "SUCCESS"));

        Map<String, Object> resp = controller.confirm(user, new ConfirmRequest("tx_1"));

        assertThat(resp.get("paymentStatus")).isEqualTo("SUCCESS");
    }
}