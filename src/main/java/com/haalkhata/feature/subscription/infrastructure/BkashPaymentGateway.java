package com.haalkhata.feature.subscription.infrastructure;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import com.haalkhata.feature.subscription.application.PaymentGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class BkashPaymentGateway implements PaymentGateway {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    /** Default token lifetime used when the gateway does not report expires_in. */
    private static final long DEFAULT_TOKEN_TTL_MILLIS = 30 * 60 * 1000L;

    /** Margin subtracted from the reported lifetime before we consider it stale. */
    private static final long TOKEN_SAFETY_MARGIN_MILLIS = 60 * 1000L;

    private final AppProperties props;

    private volatile String cachedToken;
    private volatile long tokenExpiryMillis;

    @Override
    public String name() {
        return "BKASH";
    }

    @Override
    public CreatePayment createPayment(BigDecimal amountBdt, String merchantInvoiceNumber, String payerReference) {
        if (props.getBkash().getBaseUrl() == null || props.getBkash().getBaseUrl().isBlank()) {
            throw BusinessException.badRequest("BKASH_BASE_URL is not configured");
        }
        RestClient client = RestClient.builder().baseUrl(props.getBkash().getBaseUrl()).build();
        String idToken = grantToken(client);
        Map<String, Object> createResp = client.post()
                .uri("/tokenized/checkout/create")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + idToken)
                .header("X-APP-Key", props.getBkash().getAppKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "mode", "0011",
                        "payerReference", valueOrEmpty(payerReference),
                        "callbackURL", props.getBkash().getCallbackUrl(),
                        "amount", fmtAmount(amountBdt),
                        "currency", "BDT",
                        "intent", "sale",
                        "merchantInvoiceNumber", valueOrEmpty(merchantInvoiceNumber)))
                .retrieve()
                .body(MAP);
        if (createResp == null) {
            throw BusinessException.badRequest("BKASH_CREATE_FAILED: empty create response");
        }
        String paymentId = str(createResp.get("paymentID"));
        if (paymentId == null || paymentId.isBlank()) {
            log.warn("BKASH_CREATE_FAILED statusMsg={} errorCode={} msg={} devMsg={}",
                    createResp.get("statusMessage"), createResp.get("errorCode"),
                    createResp.get("msg"), createResp.get("developerMessage"));
            throw BusinessException.badRequest("BKASH_CREATE_FAILED: no paymentID from gateway");
        }
        return new CreatePayment(
                paymentId,
                str(createResp.get("createTime")),
                str(createResp.get("transactionStatus")),
                num(createResp.get("amount")),
                str(createResp.get("currency")),
                str(createResp.get("payerReference")),
                str(createResp.get("merchantInvoiceNumber")),
                str(createResp.get("billingType")),
                str(createResp.get("bkashURL")));
    }

    @Override
    public ExecutePayment executePayment(String gatewayPaymentId) {
        RestClient client = RestClient.builder().baseUrl(props.getBkash().getBaseUrl()).build();
        String idToken = grantToken(client);
        Map<String, Object> execResp = client.post()
                .uri("/tokenized/checkout/execute")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + idToken)
                .header("X-APP-Key", props.getBkash().getAppKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("paymentID", gatewayPaymentId))
                .retrieve()
                .body(MAP);
        if (execResp == null) {
            throw BusinessException.badRequest("BKASH_EXECUTE_FAILED: empty execute response");
        }
        return new ExecutePayment(
                str(execResp.get("paymentID")),
                str(execResp.get("trxID")),
                str(execResp.get("transactionStatus")),
                num(execResp.get("amount")),
                str(execResp.get("currency")),
                str(execResp.get("payerReference")),
                str(execResp.get("merchantInvoiceNumber")),
                str(execResp.get("errorCode")),
                str(execResp.get("errorMessage")));
    }

    @Override
    public QueryPayment queryPayment(String gatewayPaymentId) {
        RestClient client = RestClient.builder().baseUrl(props.getBkash().getBaseUrl()).build();
        String idToken = grantToken(client);
        Map<String, Object> queryResp = client.post()
                .uri("/tokenized/checkout/payment/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + idToken)
                .header("X-APP-Key", props.getBkash().getAppKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("paymentID", gatewayPaymentId))
                .retrieve()
                .body(MAP);
        if (queryResp == null) {
            throw BusinessException.badRequest("BKASH_VERIFY_FAILED: empty status response");
        }
        return new QueryPayment(
                str(queryResp.get("paymentID")),
                str(queryResp.get("trxID")),
                str(queryResp.get("transactionStatus")),
                num(queryResp.get("amount")),
                str(queryResp.get("currency")),
                str(queryResp.get("payerReference")),
                str(queryResp.get("merchantInvoiceNumber")),
                str(queryResp.get("errorCode")),
                str(queryResp.get("errorMessage")));
    }

    private String grantToken(RestClient client) {
        String token = cachedToken;
        if (token != null && System.currentTimeMillis() < tokenExpiryMillis) {
            return token;
        }
        synchronized (this) {
            token = cachedToken;
            if (token != null && System.currentTimeMillis() < tokenExpiryMillis) {
                return token;
            }
            Map<String, Object> tokenResp = client.post()
                    .uri("/tokenized/checkout/token/grant")
                    .header("username", props.getBkash().getUsername())
                    .header("password", props.getBkash().getPassword())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("app_key", props.getBkash().getAppKey(),
                            "app_secret", props.getBkash().getAppSecret()))
                    .retrieve()
                    .body(MAP);
            if (tokenResp == null) {
                throw BusinessException.badRequest("BKASH_TOKEN_FAILED: empty token response");
            }
            String idToken = str(tokenResp.get("id_token"));
            if (idToken == null || idToken.isBlank()) {
                log.warn("BKASH_TOKEN_FAILED status={} msg={}", tokenResp.get("statusMessage"), tokenResp.get("msg"));
                throw BusinessException.badRequest("BKASH_TOKEN_FAILED: token not granted");
            }
            cachedToken = idToken;
            tokenExpiryMillis = tokenTtlMillis(tokenResp.get("expires_in")) + System.currentTimeMillis();
            log.info("BKASH token granted, expires_in={}ms", tokenExpiryMillis - System.currentTimeMillis() + TOKEN_SAFETY_MARGIN_MILLIS);
            return idToken;
        }
    }

    private static long tokenTtlMillis(Object expiresIn) {
        long ttl = toLong(expiresIn);
        return (ttl > 1000 ? ttl : DEFAULT_TOKEN_TTL_MILLIS) - TOKEN_SAFETY_MARGIN_MILLIS;
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                // fall back to default
            }
        }
        return -1;
    }

    private static String fmtAmount(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String num(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}