package com.llizzard.switchboard.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.llizzard.switchboard.SwitchboardContext;
import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardResolver;
import com.llizzard.switchboard.SwitchboardStrategy;
import com.llizzard.switchboard.sample.refunds.RefundGroup;
import com.llizzard.switchboard.sample.refunds.RefundRequest;
import com.llizzard.switchboard.sample.refunds.RefundResponse;

/** US2 end to end: 501 and 404 problem responses (quickstart scenarios 5 and 6). */
@SpringBootTest
@AutoConfigureMockMvc
class SampleFailureResponsesTest {

    @Autowired
    MockMvcTester mvc;

    static MockMvcRequestBuilder refund(MockMvcTester mvc, String refundType, String body) {
        MockMvcRequestBuilder request = mvc.post().uri("/api/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return refundType == null ? request : request.header("X-Refund-Type", refundType);
    }

    static void assertUnresolved(MockMvcRequestBuilder request) {
        assertThat(request)
                .hasStatus(HttpStatus.NOT_IMPLEMENTED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.errorCode", v -> v.assertThat().isEqualTo("USE_CASE_UNRESOLVED"))
                .hasPathSatisfying("$.group", v -> v.assertThat().isEqualTo("refunds"))
                .hasPathSatisfying("$.type", v -> v.assertThat().isEqualTo("urn:switchboard:error:use-case-unresolved"))
                .hasPathSatisfying("$.instance", v -> v.assertThat().isEqualTo("/api/refunds"))
                .doesNotHavePath("$.key");
    }

    static void assertNotAvailable(MockMvcRequestBuilder request, String key) {
        assertThat(request)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.errorCode", v -> v.assertThat().isEqualTo("USE_CASE_NOT_AVAILABLE"))
                .hasPathSatisfying("$.group", v -> v.assertThat().isEqualTo("refunds"))
                .hasPathSatisfying("$.key", v -> v.assertThat().isEqualTo(key))
                .hasPathSatisfying("$.type",
                        v -> v.assertThat().isEqualTo("urn:switchboard:error:use-case-not-available"));
    }

    @Test
    void noResolverMatchReturns501() {
        assertUnresolved(refund(mvc, null, "{\"amount\":50}"));
    }

    @Test
    void unknownKeyReturns404() {
        assertNotAvailable(refund(mvc, "NOPE", "{\"amount\":50}"), "NOPE");
    }

    /**
     * The consumer has its own catch-all advice, plus a strategy and a resolver that fail. The
     * library's handler still wins for its two exceptions (§R8), and never touches the others.
     */
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import({ CatchAllAdvice.class, BoomRefundStrategy.class, ExplosiveResolver.class })
    class WithConsumerCatchAllAdvice {

        @Autowired
        MockMvcTester mvc;

        @Test
        void unresolvedStillReturns501() {
            assertUnresolved(refund(mvc, null, "{\"amount\":50}"));
        }

        @Test
        void notAvailableStillReturns404() {
            assertNotAvailable(refund(mvc, "NOPE", "{\"amount\":50}"), "NOPE");
        }

        @Test
        void businessLogicFailureIsNotConvertedTo501Or404() {
            assertThat(refund(mvc, "BOOM", "{\"amount\":50}"))
                    .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyJson()
                    .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("catch-all"))
                    .hasPathSatisfying("$.exception",
                            v -> v.assertThat().isEqualTo(IllegalStateException.class.getName()));
        }

        @Test
        void failingResolverIsNotConvertedTo501Or404() {
            assertThat(refund(mvc, "STANDARD", "{\"amount\":50}").header("X-Explode", "true"))
                    .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyJson()
                    .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("catch-all"))
                    .hasPathSatisfying("$.exception",
                            v -> v.assertThat().isEqualTo(IllegalStateException.class.getName()));
        }

        @Test
        void requestWithoutExplodeHeaderIsDispatchedNormally() {
            assertThat(refund(mvc, "STANDARD", "{\"amount\":50}"))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("StandardRefundStrategy"));
        }
    }

    /** A consumer's catch-all handler, at default (lowest) precedence. */
    @RestControllerAdvice
    static class CatchAllAdvice {

        @ExceptionHandler(Exception.class)
        ResponseEntity<Map<String, String>> handleAll(Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("handledBy", "catch-all", "exception", ex.getClass().getName()));
        }
    }

    /** Business logic that fails. */
    static class BoomRefundStrategy implements SwitchboardStrategy<RefundRequest, RefundResponse> {

        @Override
        public SwitchboardGroup<RefundRequest, RefundResponse> group() {
            return RefundGroup.REFUNDS;
        }

        @Override
        public String caseKey() {
            return "BOOM";
        }

        @Override
        public RefundResponse execute(RefundRequest request, SwitchboardContext context) {
            throw new IllegalStateException("refund ledger unavailable");
        }
    }

    /** A custom signal that fails when asked to; consulted first. */
    @Order(0)
    static class ExplosiveResolver implements SwitchboardResolver<RefundRequest> {

        @Override
        public SwitchboardGroup<RefundRequest, ?> group() {
            return RefundGroup.REFUNDS;
        }

        @Override
        public String name() {
            return "explosive";
        }

        @Override
        public Optional<String> resolve(HttpServletRequest httpRequest, RefundRequest payload) {
            if ("true".equals(httpRequest.getHeader("X-Explode"))) {
                throw new IllegalStateException("signal source unavailable");
            }
            return Optional.empty();
        }
    }
}
