package com.haalkhata.feature.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.core.security.CurrentUser;
import com.haalkhata.feature.subscription.domain.PaymentTransaction;
import com.haalkhata.feature.subscription.domain.Subscription;
import com.haalkhata.feature.subscription.domain.SubscriptionPlan;
import com.haalkhata.feature.subscription.infrastructure.PaymentTransactionRepository;
import com.haalkhata.feature.subscription.infrastructure.SubscriptionPlanRepository;
import com.haalkhata.feature.subscription.infrastructure.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentTransactionRepository transactionRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @InjectMocks
    private SubscriptionService service;

    private CurrentUser user;
    private SubscriptionPlan monthlyPlan;

    @BeforeEach
    void setUp() {
        user = new CurrentUser("u1", "u@test.com", "User1", "shop_1", "OWNER", List.of("OWNER"));
        monthlyPlan = SubscriptionPlan.builder()
                .planCode("MONTHLY")
                .name("Monthly")
                .priceBdt(new BigDecimal("499.00"))
                .durationDays(30)
                .isActive(true)
                .sortOrder(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void plans_returnsActivePlans() {
        when(planRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(monthlyPlan));
        List<Map<String, Object>> result = service.plans();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("planCode")).isEqualTo("MONTHLY");
        assertThat(result.get(0).get("price")).isEqualTo("499.00");
    }

    @Test
    void purchase_createsPendingAndCallsGateway() {
        when(planRepository.findById("MONTHLY")).thenReturn(Optional.of(monthlyPlan));
        when(subscriptionRepository.findTopByShopIdAndStatusAndPlanCodeOrderByEndDateDesc(
                        "shop_1", "PENDING", "MONTHLY"))
                .thenReturn(Optional.empty());
        when(paymentGateway.createPayment(any(), any(), any()))
                .thenReturn(new PaymentGateway.CreatePayment("gw_123", "t1", "Initiated", "499.00", "BDT", "shop_1", "INV1", null, "https://sandbox.payment.bkash.com/?paymentId=gw_123"));
        ArgumentCaptor<Subscription> subCap = ArgumentCaptor.forClass(Subscription.class);
        ArgumentCaptor<PaymentTransaction> txCap = ArgumentCaptor.forClass(PaymentTransaction.class);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findByInternalTransactionId(anyString()))
                .thenReturn(Optional.of(PaymentTransaction.builder()
                        .paymentId("tx_1").subscriptionId("sub_1").shopId("shop_1")
                        .internalTransactionId("HK123").gateway("BKASH")
                        .gatewayPaymentId("gw_123").amountBdt(new BigDecimal("499.00"))
                        .status("PENDING").build()));

        Map<String, Object> resp = service.purchase(user, new SubscriptionService.PurchaseRequest("MONTHLY"));

        verify(subscriptionRepository).save(subCap.capture());
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(txCap.capture());
        Subscription sub = subCap.getValue();
        PaymentTransaction tx = txCap.getValue();
        assertThat(sub.getStatus()).isEqualTo("PENDING");
        assertThat(sub.getPlanCode()).isEqualTo("MONTHLY");
        assertThat(sub.getAmountBdt()).isEqualByComparingTo(new BigDecimal("499.00"));
        assertThat(tx.getStatus()).isEqualTo("PENDING");
        assertThat(tx.getGatewayPaymentId()).isEqualTo("gw_123");
        assertThat(tx.getAmountBdt()).isEqualByComparingTo(new BigDecimal("499.00"));
        assertThat((String) resp.get("gatewayPaymentId")).isEqualTo("gw_123");
        assertThat((String) resp.get("amount")).isEqualTo("499.00");
    }

    @Test
    void purchase_reusesPendingIfPresent() {
        Subscription existing = Subscription.builder()
                .subscriptionId("sub_1").shopId("shop_1").planCode("MONTHLY")
                .status("PENDING").amountBdt(new BigDecimal("499.00"))
                .internalTransactionId("HKEXIST001")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(planRepository.findById("MONTHLY")).thenReturn(Optional.of(monthlyPlan));
        when(subscriptionRepository.findTopByShopIdAndStatusAndPlanCodeOrderByEndDateDesc(
                        "shop_1", "PENDING", "MONTHLY"))
                .thenReturn(Optional.of(existing));
        PaymentTransaction existingTx = PaymentTransaction.builder()
                .paymentId("tx_1").subscriptionId("sub_1").shopId("shop_1")
                .internalTransactionId("HKEXIST001").gateway("BKASH")
                .amountBdt(new BigDecimal("499.00")).status("PENDING")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.findByInternalTransactionId("HKEXIST001")).thenReturn(Optional.of(existingTx));

        Map<String, Object> resp = service.purchase(user, new SubscriptionService.PurchaseRequest("MONTHLY"));

        verify(paymentGateway, never()).createPayment(any(), any(), any());
        assertThat(resp.get("subscriptionId")).isEqualTo("sub_1");
        assertThat(resp.get("paymentId")).isEqualTo("tx_1");
    }

    @Test
    void purchase_recreatesPaymentWhenPendingTransactionStale() {
        Subscription existing = Subscription.builder()
                .subscriptionId("sub_1").shopId("shop_1").planCode("MONTHLY")
                .status("PENDING").amountBdt(new BigDecimal("499.00"))
                .internalTransactionId("HKEXIST001")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(planRepository.findById("MONTHLY")).thenReturn(Optional.of(monthlyPlan));
        when(subscriptionRepository.findTopByShopIdAndStatusAndPlanCodeOrderByEndDateDesc(
                        "shop_1", "PENDING", "MONTHLY"))
                .thenReturn(Optional.of(existing));
        PaymentTransaction existingTx = PaymentTransaction.builder()
                .paymentId("tx_1").subscriptionId("sub_1").shopId("shop_1")
                .internalTransactionId("HKEXIST001").gateway("BKASH")
                .amountBdt(new BigDecimal("499.00")).status("PENDING")
                .gatewayPaymentId("OLD_GW_1")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.findByInternalTransactionId("HKEXIST001"))
                .thenReturn(Optional.of(existingTx));
        when(paymentGateway.queryPayment("OLD_GW_1"))
                .thenReturn(new PaymentGateway.QueryPayment("OLD_GW_1", null, "Initiated",
                        "499.00", "BDT", "shop_1", null, null, null));
        when(paymentGateway.createPayment(any(), any(), any()))
                .thenReturn(new PaymentGateway.CreatePayment("NEW_GW_1", "t1", "Initiated",
                        "499.00", "BDT", "shop_1", "HKEXIST001-X", null,
                        "https://sandbox.payment.bkash.com/?paymentId=NEW_GW_1"));

        Map<String, Object> resp = service.purchase(user, new SubscriptionService.PurchaseRequest("MONTHLY"));

        verify(paymentGateway, times(1)).queryPayment("OLD_GW_1");
        verify(paymentGateway, times(1)).createPayment(any(), any(), any());
        assertThat(resp.get("subscriptionId")).isEqualTo("sub_1");
        assertThat(resp.get("paymentId")).isEqualTo("tx_1");
        assertThat(resp.get("gatewayPaymentId")).isEqualTo("NEW_GW_1");
        assertThat((String) resp.get("bkashUrl")).contains("NEW_GW_1");
    }

    @Test
    void purchase_activatesWhenPendingTransactionAlreadyPaid() {
        Subscription existing = Subscription.builder()
                .subscriptionId("sub_1").shopId("shop_1").planCode("MONTHLY")
                .status("PENDING").amountBdt(new BigDecimal("499.00"))
                .internalTransactionId("HKEXIST001")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(planRepository.findById("MONTHLY")).thenReturn(Optional.of(monthlyPlan));
        when(subscriptionRepository.findTopByShopIdAndStatusAndPlanCodeOrderByEndDateDesc(
                        "shop_1", "PENDING", "MONTHLY"))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.findById("sub_1")).thenReturn(Optional.of(existing));
        PaymentTransaction existingTx = PaymentTransaction.builder()
                .paymentId("tx_1").subscriptionId("sub_1").shopId("shop_1")
                .internalTransactionId("HKEXIST001").gateway("BKASH")
                .amountBdt(new BigDecimal("499.00")).status("PENDING")
                .gatewayPaymentId("OLD_GW_1")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.findByInternalTransactionId("HKEXIST001"))
                .thenReturn(Optional.of(existingTx));
        when(paymentGateway.queryPayment("OLD_GW_1"))
                .thenReturn(new PaymentGateway.QueryPayment("OLD_GW_1", "TRX1", "Completed",
                        "499.00", "BDT", "shop_1", null, null, null));
        when(paymentGateway.executePayment("OLD_GW_1"))
                .thenReturn(new PaymentGateway.ExecutePayment("OLD_GW_1", "TRX1", "Completed",
                        "499.00", "BDT", "shop_1", null, null, null));
        when(subscriptionRepository.findTopByShopIdAndStatusOrderByEndDateDesc("shop_1", "ACTIVE"))
                .thenReturn(Optional.empty());

        Map<String, Object> resp = service.purchase(user, new SubscriptionService.PurchaseRequest("MONTHLY"));

        verify(paymentGateway, never()).createPayment(any(), any(), any());
        assertThat((String) resp.get("paymentStatus")).isEqualTo("SUCCESS");
        assertThat((String) resp.get("subscriptionStatus")).isEqualTo("ACTIVE");
    }

    @Test
    void purchase_throwsIfPlanNotFound() {
        when(planRepository.findById("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.purchase(user, new SubscriptionService.PurchaseRequest("UNKNOWN")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("SUBSCRIPTION_PLAN_NOT_FOUND"));
    }

    @Test
    void confirm_success_activatesSubscription() {
        Subscription sub = Subscription.builder()
                .subscriptionId("sub_1").shopId("shop_1").planCode("MONTHLY")
                .status("PENDING").amountBdt(new BigDecimal("499.00"))
                .internalTransactionId("HK123")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        PaymentTransaction tx = PaymentTransaction.builder()
                .paymentId("tx_1").subscriptionId("sub_1").shopId("shop_1")
                .internalTransactionId("HK123").gateway("BKASH")
                .gatewayPaymentId("gw_123").amountBdt(new BigDecimal("499.00")).status("PENDING")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.findByPaymentIdAndShopId("tx_1", "shop_1")).thenReturn(Optional.of(tx));
        when(subscriptionRepository.findById("sub_1")).thenReturn(Optional.of(sub));
        when(paymentGateway.executePayment("gw_123"))
                .thenReturn(new PaymentGateway.ExecutePayment("gw_123", "TRX1", "Completed", "499.00", "BDT", "shop_1", "HK123", null, null));
        when(subscriptionRepository.findTopByShopIdAndStatusOrderByEndDateDesc("shop_1", "ACTIVE"))
                .thenReturn(Optional.empty());
        when(planRepository.findById("MONTHLY")).thenReturn(Optional.of(monthlyPlan));
        ArgumentCaptor<Subscription> subCap = ArgumentCaptor.forClass(Subscription.class);
        ArgumentCaptor<PaymentTransaction> txCap = ArgumentCaptor.forClass(PaymentTransaction.class);

        Map<String, Object> resp = service.confirm(user, new SubscriptionService.ConfirmRequest("tx_1"));

        verify(subscriptionRepository).save(subCap.capture());
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(txCap.capture());
        assertThat(txCap.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(txCap.getValue().getGatewayTransactionId()).isEqualTo("TRX1");
        assertThat(subCap.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(subCap.getValue().getStartDate()).isNotNull();
        assertThat(subCap.getValue().getEndDate()).isAfter(subCap.getValue().getStartDate());
        assertThat((String) resp.get("subscriptionStatus")).isEqualTo("ACTIVE");
        assertThat((String) resp.get("paymentStatus")).isEqualTo("SUCCESS");
    }

    @Test
    void confirm_idempotentIfAlreadySuccess() {
        Subscription sub = Subscription.builder()
                .subscriptionId("sub_1").shopId("shop_1").planCode("MONTHLY")
                .status("ACTIVE").amountBdt(new BigDecimal("499.00"))
                .internalTransactionId("HK123")
                .startDate(LocalDateTime.now().minusDays(5))
                .endDate(LocalDateTime.now().plusDays(25))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        PaymentTransaction tx = PaymentTransaction.builder()
                .paymentId("tx_1").subscriptionId("sub_1").shopId("shop_1")
                .internalTransactionId("HK123").gateway("BKASH")
                .gatewayPaymentId("gw_123").gatewayTransactionId("TRX1")
                .amountBdt(new BigDecimal("499.00")).status("SUCCESS")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.findByPaymentIdAndShopId("tx_1", "shop_1")).thenReturn(Optional.of(tx));
        when(subscriptionRepository.findById("sub_1")).thenReturn(Optional.of(sub));

        Map<String, Object> resp = service.confirm(user, new SubscriptionService.ConfirmRequest("tx_1"));

        verify(paymentGateway, never()).executePayment(any());
        assertThat(resp.get("subscriptionStatus")).isEqualTo("ACTIVE");
        assertThat(resp.get("paymentStatus")).isEqualTo("SUCCESS");
    }

    @Test
    void confirm_failCancelledDoesNotActivate() {
        Subscription sub = Subscription.builder()
                .subscriptionId("sub_1").shopId("shop_1").planCode("MONTHLY")
                .status("PENDING").amountBdt(new BigDecimal("499.00"))
                .internalTransactionId("HK123")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        PaymentTransaction tx = PaymentTransaction.builder()
                .paymentId("tx_1").subscriptionId("sub_1").shopId("shop_1")
                .internalTransactionId("HK123").gateway("BKASH")
                .gatewayPaymentId("gw_123").amountBdt(new BigDecimal("499.00")).status("PENDING")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.findByPaymentIdAndShopId("tx_1", "shop_1")).thenReturn(Optional.of(tx));
        when(subscriptionRepository.findById("sub_1")).thenReturn(Optional.of(sub));
        when(paymentGateway.executePayment("gw_123"))
                .thenReturn(new PaymentGateway.ExecutePayment("gw_123", null, "Cancel", "499.00", "BDT", "shop_1", "HK123", null, null));
        ArgumentCaptor<PaymentTransaction> txCap = ArgumentCaptor.forClass(PaymentTransaction.class);

        Map<String, Object> resp = service.confirm(user, new SubscriptionService.ConfirmRequest("tx_1"));

        verify(transactionRepository, org.mockito.Mockito.times(2)).save(txCap.capture());
        assertThat(txCap.getValue().getStatus()).isEqualTo("CANCELLED");
        assertThat(resp.get("paymentStatus")).isEqualTo("CANCELLED");
        assertThat(resp.get("subscriptionStatus")).isEqualTo("CANCELLED");
    }

    @Test
    void confirm_amountMismatchMarkFailedAndThrows() {
        Subscription sub = Subscription.builder()
                .subscriptionId("sub_1").shopId("shop_1").planCode("MONTHLY")
                .status("PENDING").amountBdt(new BigDecimal("499.00"))
                .internalTransactionId("HK123")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        PaymentTransaction tx = PaymentTransaction.builder()
                .paymentId("tx_1").subscriptionId("sub_1").shopId("shop_1")
                .internalTransactionId("HK123").gateway("BKASH")
                .gatewayPaymentId("gw_123").amountBdt(new BigDecimal("499.00")).status("PENDING")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.findByPaymentIdAndShopId("tx_1", "shop_1")).thenReturn(Optional.of(tx));
        when(subscriptionRepository.findById("sub_1")).thenReturn(Optional.of(sub));
        when(paymentGateway.executePayment("gw_123"))
                .thenReturn(new PaymentGateway.ExecutePayment("gw_123", "TRX1", "Completed", "500.00", "BDT", "shop_1", "HK123", null, null));
        ArgumentCaptor<PaymentTransaction> txCap = ArgumentCaptor.forClass(PaymentTransaction.class);
        ArgumentCaptor<Subscription> subCap = ArgumentCaptor.forClass(Subscription.class);

        assertThatThrownBy(() -> service.confirm(user, new SubscriptionService.ConfirmRequest("tx_1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PAYMENT_AMOUNT_MISMATCH"));

        verify(transactionRepository, org.mockito.Mockito.times(2)).save(txCap.capture());
        verify(subscriptionRepository).save(subCap.capture());
        assertThat(txCap.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(subCap.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void confirmExtendFromActiveEndDate() {
        Subscription currentActive = Subscription.builder()
                .subscriptionId("sub_0").shopId("shop_1").planCode("MONTHLY")
                .status("ACTIVE").amountBdt(new BigDecimal("499.00"))
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().plusDays(20))
                .createdAt(LocalDateTime.now().minusDays(10)).updatedAt(LocalDateTime.now().minusDays(10)).build();
        Subscription sub = Subscription.builder()
                .subscriptionId("sub_1").shopId("shop_1").planCode("MONTHLY")
                .status("PENDING").amountBdt(new BigDecimal("499.00"))
                .internalTransactionId("HK123")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        PaymentTransaction tx = PaymentTransaction.builder()
                .paymentId("tx_1").subscriptionId("sub_1").shopId("shop_1")
                .internalTransactionId("HK123").gateway("BKASH")
                .gatewayPaymentId("gw_123").amountBdt(new BigDecimal("499.00")).status("PENDING")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(transactionRepository.findByPaymentIdAndShopId("tx_1", "shop_1")).thenReturn(Optional.of(tx));
        when(subscriptionRepository.findById("sub_1")).thenReturn(Optional.of(sub));
        when(paymentGateway.executePayment("gw_123"))
                .thenReturn(new PaymentGateway.ExecutePayment("gw_123", "TRX1", "Completed", "499.00", "BDT", "shop_1", "HK123", null, null));
        when(subscriptionRepository.findTopByShopIdAndStatusOrderByEndDateDesc("shop_1", "ACTIVE"))
                .thenReturn(Optional.of(currentActive));
        when(planRepository.findById("MONTHLY")).thenReturn(Optional.of(monthlyPlan));
        ArgumentCaptor<Subscription> subCap = ArgumentCaptor.forClass(Subscription.class);

        service.confirm(user, new SubscriptionService.ConfirmRequest("tx_1"));

        verify(subscriptionRepository).save(subCap.capture());
        assertThat(subCap.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(subCap.getValue().getStartDate()).isAfterOrEqualTo(currentActive.getEndDate());
        assertThat(subCap.getValue().getEndDate()).isEqualTo(subCap.getValue().getStartDate().plusDays(30));
    }

    @Test
    void confirm_throwsIfPaymentNotFoundForShop() {
        when(transactionRepository.findByPaymentIdAndShopId("tx_1", "shop_1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm(user, new SubscriptionService.ConfirmRequest("tx_1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void status_returnsActiveInfo() {
        Subscription active = Subscription.builder()
                .subscriptionId("sub_1").shopId("shop_1").planCode("MONTHLY")
                .status("ACTIVE").amountBdt(new BigDecimal("499.00"))
                .startDate(LocalDateTime.now().minusDays(5))
                .endDate(LocalDateTime.now().plusDays(25).plusHours(1))
                .createdAt(LocalDateTime.now().minusDays(5)).updatedAt(LocalDateTime.now().minusDays(5)).build();
        when(subscriptionRepository.findByShopIdOrderByCreatedAtDesc("shop_1"))
                .thenReturn(List.of(active));
        when(planRepository.findById("MONTHLY")).thenReturn(Optional.of(monthlyPlan));

        Map<String, Object> result = service.status(user);

        assertThat(result.get("hasActive")).isEqualTo(true);
        assertThat(result.get("daysRemaining")).isEqualTo(25L);
        assertThat(result.get("subscription")).isNotNull();
    }
}
