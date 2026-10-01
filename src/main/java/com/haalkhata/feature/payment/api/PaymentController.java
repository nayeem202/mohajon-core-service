package com.haalkhata.feature.payment.api;

import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.payment.application.PaymentService;
import com.haalkhata.feature.payment.application.PaymentService.AddPaymentRequest;
import com.haalkhata.feature.payment.application.PaymentService.PaymentMethodRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/api/v1/payments")
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return paymentService.list(user);
    }

    @GetMapping("/api/v1/payments/customer/{customerId}")
    public List<Map<String, Object>> listForCustomer(@AuthenticationPrincipal CurrentUser user,
                                                     @PathVariable String customerId) {
        return paymentService.listForCustomer(user, customerId);
    }

    @PostMapping("/api/v1/payments")
    public Map<String, Object> add(@AuthenticationPrincipal CurrentUser user,
                                   @Valid @RequestBody AddPaymentRequest request) {
        return paymentService.addPayment(user, request);
    }

    @DeleteMapping("/api/v1/payments/{paymentId}")
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String paymentId) {
        paymentService.delete(user, paymentId);
    }

    // -------- Configurable payment methods --------

    @GetMapping("/api/v1/payment-methods")
    public List<Map<String, Object>> methods(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return paymentService.methods(includeDisabled);
    }

    @PutMapping("/api/v1/payment-methods")
    public Map<String, Object> upsertMethod(@AuthenticationPrincipal CurrentUser user,
                                            @Valid @RequestBody PaymentMethodRequest request) {
        return paymentService.upsertMethod(user, request);
    }

    @DeleteMapping("/api/v1/payment-methods/{code}")
    public void deleteMethod(@AuthenticationPrincipal CurrentUser user, @PathVariable String code) {
        paymentService.deleteMethod(user, code);
    }
}
