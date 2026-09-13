package com.haalkhata.feature.subscription.api;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.feature.subscription.application.SubscriptionService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/bkash")
@RequiredArgsConstructor
public class BkashCallbackController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestParam Map<String, String> params) {
        String paymentId = params.get("paymentID");
        if (paymentId == null || paymentId.isBlank()) {
            paymentId = params.get("paymentId");
        }
        log.info("BKASH_CALLBACK_RECEIVED paymentID={} extras={}", paymentId, params.keySet());
        try {
            return ResponseEntity.ok(subscriptionService.confirmFromCallback(paymentId));
        } catch (BusinessException ex) {
            log.warn("BKASH_CALLBACK_REJECTED paymentID={} code={} message={}",
                    paymentId, ex.getCode(), ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("BKASH_CALLBACK_ERROR paymentID={}", paymentId, ex);
            return ResponseEntity.internalServerError().build();
        }
    }
}