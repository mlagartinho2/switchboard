package com.llizzard.switchboard.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.security.Principal;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.llizzard.switchboard.SwitchboardDispatchException;
import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardNotAvailableException;
import com.llizzard.switchboard.SwitchboardResolver;
import com.llizzard.switchboard.SwitchboardUnresolvedException;
import com.llizzard.switchboard.sample.payments.CardPaymentStrategy;
import com.llizzard.switchboard.sample.refunds.RefundGroup;
import com.llizzard.switchboard.sample.refunds.RefundRequest;

/** US1 end to end: dispatch, first-resolver-wins, group isolation, identity-based resolver. */
@SpringBootTest
@AutoConfigureMockMvc
class SampleDispatchTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoSpyBean
    CardPaymentStrategy cardPaymentStrategy;

    static MockMvcRequestBuilder refund(MockMvcTester mvc, String refundType, String body) {
        MockMvcRequestBuilder request = mvc.post().uri("/api/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return refundType == null ? request : request.header("X-Refund-Type", refundType);
    }

    /**
     * The request was rejected by the dispatcher with the given exception.
     *
     * <p>TODO(T038): once {@code SwitchboardExceptionHandler} exists (Phase 4), these rejections
     * become 404 / 501 problem responses. Replace this helper with status assertions then:
     * {@link SwitchboardNotAvailableException} → 404, {@link SwitchboardUnresolvedException} → 501.
     */
    static void assertRejectedWith(MvcTestResult result, Class<? extends SwitchboardDispatchException> type) {
        assertThat(result).hasFailed();
        // MockMvc wraps unhandled exceptions in a ServletException ("Request processing failed").
        assertThat(result.getUnresolvedException()).cause().isInstanceOf(type);
    }

    @Test
    void headerStandardIsHandledByStandard() {
        assertThat(refund(mvc, "STANDARD", "{\"amount\":50}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("StandardRefundStrategy"))
                .doesNotHavePath("$.expectedSettlementHours");
    }

    @Test
    void noHeaderLargeAmountIsHandledByExpedited() {
        assertThat(refund(mvc, null, "{\"amount\":5000}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("ExpeditedRefundStrategy"))
                .hasPathSatisfying("$.expectedSettlementHours", v -> v.assertThat().isEqualTo(4));
    }

    @Test
    void firstResolverWinsWhenBothCouldAnswer() {
        assertThat(refund(mvc, "STANDARD", "{\"amount\":5000}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("StandardRefundStrategy"));
    }

    @Test
    void keyFromAnotherGroupIsNotAvailableAndNeverRunsThere() {
        assertRejectedWith(refund(mvc, "CARD", "{\"amount\":50}").exchange(), SwitchboardNotAvailableException.class);

        verify(cardPaymentStrategy, never()).execute(any(), any());
    }

    @Test
    void paymentsCardIsHandledByCardPaymentStrategy() {
        assertThat(mvc.post().uri("/api/payments")
                        .header("X-Payment-Method", "CARD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"o-1\",\"amount\":20}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("CardPaymentStrategy"));
    }

    @Test
    void noSpringSecurityOnTheClasspath() {
        assertThat(classPresent("org.springframework.security.core.context.SecurityContextHolder")).isFalse();
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, SampleDispatchTest.class.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException ex) {
            return false;
        }
    }

    /** US1 scenario 4 / FR-009: a resolver that identifies the use case from the caller's identity. */
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(CallerIdentityResolver.class)
    class WithCallerIdentityResolver {

        @Autowired
        MockMvcTester mvc;

        @Test
        void vipCallerIsHandledByExpedited() {
            assertThat(refund(mvc, null, "{\"amount\":50}").principal(principal("vip-client")))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("ExpeditedRefundStrategy"));
        }

        @Test
        void regularCallerFallsThroughToUnresolved() {
            assertRejectedWith(refund(mvc, null, "{\"amount\":50}").principal(principal("regular-client")).exchange(),
                    SwitchboardUnresolvedException.class);
        }

        @Test
        void anonymousCallerFallsThroughToUnresolved() {
            assertRejectedWith(refund(mvc, null, "{\"amount\":50}").exchange(), SwitchboardUnresolvedException.class);
        }

        private static Principal principal(String name) {
            return () -> name;
        }
    }

    /** Test-only identity resolver: consulted first, reads the servlet principal (no Spring Security). */
    @Order(0)
    static class CallerIdentityResolver implements SwitchboardResolver<RefundRequest> {

        @Override
        public SwitchboardGroup<RefundRequest, ?> group() {
            return RefundGroup.REFUNDS;
        }

        @Override
        public String name() {
            return "caller-identity";
        }

        @Override
        public Optional<String> resolve(HttpServletRequest httpRequest, RefundRequest payload) {
            Principal principal = httpRequest.getUserPrincipal();
            return principal != null && "vip-client".equals(principal.getName())
                    ? Optional.of("EXPEDITED")
                    : Optional.empty();
        }
    }
}
