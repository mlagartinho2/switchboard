package com.llizzard.switchboard.sample.refunds;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Plain JUnit, no Spring context: every SwitchboardResolver has unit tests (constitution). */
class RefundResolversTest {

    private final MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/refunds");

    private static RefundRequest amount(String amount) {
        return new RefundRequest("o-1", new BigDecimal(amount));
    }

    // --- RefundTypeHeaderResolver ---

    private final RefundTypeHeaderResolver header = new RefundTypeHeaderResolver();

    @Test
    void headerResolverIdentity() {
        assertThat(header.name()).isEqualTo("refund-type-header");
        assertThat(header.group()).isEqualTo(RefundGroup.REFUNDS);
    }

    @Test
    void headerResolverReturnsHeaderValue() {
        http.addHeader("X-Refund-Type", "STANDARD");

        assertThat(header.resolve(http, amount("50"))).contains("STANDARD");
    }

    @Test
    void headerResolverReturnsEmptyWithoutHeader() {
        assertThat(header.resolve(http, amount("50"))).isEmpty();
    }

    // --- RefundAmountResolver ---

    private final RefundAmountResolver byAmount = new RefundAmountResolver();

    @Test
    void amountResolverIdentity() {
        assertThat(byAmount.name()).isEqualTo("refund-amount");
        assertThat(byAmount.group()).isEqualTo(RefundGroup.REFUNDS);
    }

    @Test
    void amountAboveThresholdIsExpedited() {
        assertThat(byAmount.resolve(http, amount("1000.01"))).contains("EXPEDITED");
        assertThat(byAmount.resolve(http, amount("5000"))).contains("EXPEDITED");
    }

    @Test
    void amountAtOrBelowThresholdGivesNoKey() {
        assertThat(byAmount.resolve(http, amount("1000"))).isEmpty();
        assertThat(byAmount.resolve(http, amount("1000.00"))).isEmpty();
        assertThat(byAmount.resolve(http, amount("50"))).isEmpty();
    }

    @Test
    void nullPayloadOrAmountGivesNoKey() {
        assertThat(byAmount.resolve(http, null)).isEmpty();
        assertThat(byAmount.resolve(http, new RefundRequest("o-1", null))).isEmpty();
    }
}
