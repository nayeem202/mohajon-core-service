package com.haalkhata.feature.subscription.application;

import java.math.BigDecimal;

public interface PaymentGateway {

    CreatePayment createPayment(BigDecimal amountBdt, String merchantInvoiceNumber, String payerReference);

    ExecutePayment executePayment(String gatewayPaymentId);

    QueryPayment queryPayment(String gatewayPaymentId);

    String name();

    record CreatePayment(String paymentId, String createTime, String transactionStatus,
                         String amount, String currency, String payerReference,
                         String merchantInvoiceNumber, String billingType, String bkashUrl) {
    }

    record ExecutePayment(String paymentId, String trxId, String transactionStatus,
                          String amount, String currency, String payerReference,
                          String merchantInvoiceNumber, String errorCode, String errorMessage) {
    }

    record QueryPayment(String paymentId, String trxId, String transactionStatus,
                        String amount, String currency, String payerReference,
                        String merchantInvoiceNumber, String errorCode, String errorMessage) {
    }
}