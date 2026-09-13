package com.haalkhata.feature.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "subscription_id", nullable = false, length = 36)
    private String subscriptionId;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "internal_transaction_id", nullable = false, length = 64)
    private String internalTransactionId;

    @Column(name = "gateway_payment_id", length = 128)
    private String gatewayPaymentId;

    @Column(name = "gateway_transaction_id", length = 128)
    private String gatewayTransactionId;

    @Column(nullable = false, length = 20)
    private String gateway;

    @Column(name = "amount_bdt", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountBdt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "payment_create_time")
    private LocalDateTime paymentCreateTime;

    @Column(name = "execute_time")
    private LocalDateTime executeTime;

    @Column(name = "paid_time")
    private LocalDateTime paidTime;

    @Column(name = "payer_reference", length = 60)
    private String payerReference;

    @Column(name = "merchant_invoice_number", length = 64)
    private String merchantInvoiceNumber;

    @Column(name = "billing_type", length = 20)
    private String billingType;

    @Column(name = "bkash_url", length = 500)
    private String bkashUrl;

    @Column(name = "error_code", length = 30)
    private String errorCode;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}