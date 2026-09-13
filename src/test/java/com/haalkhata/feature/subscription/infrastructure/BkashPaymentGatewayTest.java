package com.haalkhata.feature.subscription.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BkashPaymentGatewayTest {

    @Test
    void name_isBkash() {
        BkashPaymentGateway gateway = new BkashPaymentGateway(new AppProperties());
        assertThat(gateway.name()).isEqualTo("BKASH");
    }

    @Test
    void createPayment_rejectsMissingBaseUrl() {
        BkashPaymentGateway gateway = new BkashPaymentGateway(new AppProperties());
        assertThatThrownBy(() -> gateway.createPayment(new BigDecimal("499.00"), "HK123", "shop_1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("BAD_REQUEST"));
    }
}