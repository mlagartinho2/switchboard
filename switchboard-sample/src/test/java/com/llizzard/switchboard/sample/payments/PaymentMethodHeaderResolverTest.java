package com.llizzard.switchboard.sample.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Plain JUnit, no Spring context. */
class PaymentMethodHeaderResolverTest {

    private final MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/payments");
    private final PaymentMethodHeaderResolver resolver = new PaymentMethodHeaderResolver();
    private final PaymentRequest payload = new PaymentRequest("o-1", new BigDecimal("20"));

    @Test
    void identity() {
        assertThat(resolver.name()).isEqualTo("payment-method-header");
        assertThat(resolver.group()).isEqualTo(PaymentGroup.PAYMENTS);
    }

    @Test
    void returnsHeaderValue() {
        http.addHeader("X-Payment-Method", "CARD");

        assertThat(resolver.resolve(http, payload)).contains("CARD");
    }

    @Test
    void returnsEmptyWithoutHeader() {
        assertThat(resolver.resolve(http, payload)).isEmpty();
    }
}
