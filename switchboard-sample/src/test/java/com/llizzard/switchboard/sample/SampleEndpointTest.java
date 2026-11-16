package com.llizzard.switchboard.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** US4 end to end: the inspection report (quickstart scenario 8, contracts/actuator-endpoint.md). */
@SpringBootTest(properties = {
        "switchboard.groups.refunds.cases.[EXPEDITED].enabled=false",
        "switchboard.groups.refunds.cases.[LEGACY].owner=payments-team" })
@AutoConfigureMockMvc
class SampleEndpointTest {

    private static final String REFUNDS = "$.groups.refunds";

    @Autowired
    MockMvcTester mvc;

    @Test
    void reportShowsBothGroupsWithTheirEffectiveOrder() {
        assertThat(mvc.get().uri("/actuator/switchboard"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .hasPathSatisfying("$.groups", v -> v.assertThat().asMap().containsOnlyKeys("payments", "refunds"))
                .hasPathSatisfying(REFUNDS + ".resolutionOrder",
                        v -> v.assertThat().isEqualTo(List.of("refund-type-header", "refund-amount")))
                .hasPathSatisfying("$.groups.payments.resolutionOrder",
                        v -> v.assertThat().isEqualTo(List.of("payment-method-header")))
                .hasPathSatisfying("$.groups.payments.cases[0].key", v -> v.assertThat().isEqualTo("CARD"));
    }

    @Test
    void refundCasesAreSortedAndShowEnabledAndRegisteredSeparately() {
        assertThat(mvc.get().uri("/actuator/switchboard"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .hasPathSatisfying(REFUNDS + ".cases[*].key",
                        v -> v.assertThat().isEqualTo(List.of("EXPEDITED", "LEGACY", "STANDARD")))
                // disabled but registered
                .hasPathSatisfying(REFUNDS + ".cases[0].enabled", v -> v.assertThat().isEqualTo(false))
                .hasPathSatisfying(REFUNDS + ".cases[0].registered", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying(REFUNDS + ".cases[0].strategyClass",
                        v -> v.assertThat().isEqualTo(
                                "com.llizzard.switchboard.sample.refunds.ExpeditedRefundStrategy"))
                // configured but never deployed
                .hasPathSatisfying(REFUNDS + ".cases[1].enabled", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying(REFUNDS + ".cases[1].registered", v -> v.assertThat().isEqualTo(false))
                .hasPathSatisfying(REFUNDS + ".cases[1].strategyClass", v -> v.assertThat().isNull())
                .hasPathSatisfying(REFUNDS + ".cases[1].owner", v -> v.assertThat().isEqualTo("payments-team"))
                // enabled and registered, metadata from application.yml
                .hasPathSatisfying(REFUNDS + ".cases[2].enabled", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying(REFUNDS + ".cases[2].registered", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying(REFUNDS + ".cases[2].owner", v -> v.assertThat().isEqualTo("payments-team"))
                .hasPathSatisfying(REFUNDS + ".cases[2].version", v -> v.assertThat().isEqualTo("1.0"));
    }
}
