package com.haalkhata.feature.subscription.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.feature.subscription.application.SubscriptionService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BkashCallbackControllerTest {

    @Test
    void callback_delegatesToService() {
        SubscriptionService service = Mockito.mock(SubscriptionService.class);
        BkashCallbackController controller = new BkashCallbackController(service);
        when(service.confirmFromCallback("gw_123")).thenReturn(Map.of("paymentStatus", "SUCCESS"));

        ResponseEntity<Map<String, Object>> resp = controller.callback(Map.of("paymentID", "gw_123"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("paymentStatus")).isEqualTo("SUCCESS");
    }

    @Test
    void callback_reportsBusinessErrorAsBadRequest() {
        SubscriptionService service = Mockito.mock(SubscriptionService.class);
        BkashCallbackController controller = new BkashCallbackController(service);
        when(service.confirmFromCallback("unknown")).thenThrow(BusinessException.notFound("Unknown paymentID"));

        ResponseEntity<Map<String, Object>> resp = controller.callback(Map.of("paymentID", "unknown"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void callback_missingPaymentIdIsBadRequest() {
        SubscriptionService service = Mockito.mock(SubscriptionService.class);
        BkashCallbackController controller = new BkashCallbackController(service);
        when(service.confirmFromCallback(null)).thenThrow(BusinessException.badRequest("paymentID is required"));

        ResponseEntity<Map<String, Object>> resp = controller.callback(Map.of());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("code")).isEqualTo("BAD_REQUEST");
    }
}