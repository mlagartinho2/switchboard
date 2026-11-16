package com.llizzard.switchboard.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.llizzard.switchboard.SwitchboardContext;
import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardStrategy;
import com.llizzard.switchboard.sample.refunds.RefundGroup;
import com.llizzard.switchboard.sample.refunds.RefundRequest;
import com.llizzard.switchboard.sample.refunds.RefundResponse;

/** SC-001: a new use case is reachable with no change to main sources or configuration. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SampleNewUseCaseTest.PartialRefundStrategy.class)
class SampleNewUseCaseTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void newStrategyIsReachableByItsKey() {
        assertThat(mvc.post().uri("/api/refunds")
                        .header("X-Refund-Type", "PARTIAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"o-1\",\"amount\":50}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("PartialRefundStrategy"))
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("PARTIALLY_REFUNDED"));
    }

    static class PartialRefundStrategy implements SwitchboardStrategy<RefundRequest, RefundResponse> {

        @Override
        public SwitchboardGroup<RefundRequest, RefundResponse> group() {
            return RefundGroup.REFUNDS;
        }

        @Override
        public String caseKey() {
            return "PARTIAL";
        }

        @Override
        public RefundResponse execute(RefundRequest request, SwitchboardContext context) {
            return new RefundResponse("PartialRefundStrategy", "PARTIALLY_REFUNDED", null);
        }
    }
}
