package com.llizzard.switchboard.sample;

import static com.llizzard.switchboard.sample.SampleDispatchTest.assertNotAvailable;
import static com.llizzard.switchboard.sample.SampleDispatchTest.assertUnresolved;
import static com.llizzard.switchboard.sample.SampleDispatchTest.refund;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.llizzard.switchboard.SwitchboardDispatcher;
import com.llizzard.switchboard.SwitchboardRegistry;

/** US3 end to end: every operator control is configuration-only (quickstart §4). */
class SampleConfigurationTest {

    /** (a) Kill switch: one case off, everything else untouched (SC-003). */
    @Nested
    @SpringBootTest(properties = "switchboard.groups.refunds.cases.[EXPEDITED].enabled=false")
    @AutoConfigureMockMvc
    class ExpeditedDisabled {

        @Autowired
        MockMvcTester mvc;

        @Autowired
        SwitchboardRegistry registry;

        @Test
        void disabledCaseReturns404() {
            assertNotAvailable(refund(mvc, null, "{\"amount\":5000}"), "EXPEDITED");
        }

        @Test
        void disabledCaseIsStillRegistered() {
            assertThat(registry.registered("refunds")).containsKey("EXPEDITED");
            assertThat(registry.isEnabled("refunds", "EXPEDITED")).isFalse();
        }

        @Test
        void otherCasesInTheGroupStillWork() {
            assertThat(refund(mvc, "STANDARD", "{\"amount\":50}"))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("StandardRefundStrategy"));
        }

        @Test
        void otherGroupsStillWork() {
            assertThat(mvc.post().uri("/api/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Payment-Method", "CARD")
                    .content("{\"amount\":10}"))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("CardPaymentStrategy"));
        }
    }

    /** (b) Reversed order: the amount resolver now wins over the header. */
    @Nested
    @SpringBootTest(properties = "switchboard.groups.refunds.resolution-order=refund-amount,refund-type-header")
    @AutoConfigureMockMvc
    class ReversedResolutionOrder {

        @Autowired
        MockMvcTester mvc;

        @Test
        void amountResolverIsConsultedFirst() {
            assertThat(refund(mvc, "STANDARD", "{\"amount\":5000}"))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("ExpeditedRefundStrategy"));
        }
    }

    /** (c) Trimmed order: the amount resolver is never consulted. */
    @Nested
    @SpringBootTest(properties = "switchboard.groups.refunds.resolution-order=refund-type-header")
    @AutoConfigureMockMvc
    class TrimmedResolutionOrder {

        @Autowired
        MockMvcTester mvc;

        @Test
        void largeAmountWithoutHeaderIsUnresolved() {
            assertUnresolved(refund(mvc, null, "{\"amount\":5000}"));
        }

        @Test
        void headerStillWorks() {
            assertThat(refund(mvc, "EXPEDITED", "{\"amount\":50}"))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .hasPathSatisfying("$.handledBy", v -> v.assertThat().isEqualTo("ExpeditedRefundStrategy"));
        }
    }

    /** (d) Library switched off: the application starts, and the dispatch endpoints are absent (§R11). */
    @Nested
    @SpringBootTest(properties = "switchboard.enabled=false")
    @AutoConfigureMockMvc
    class LibraryDisabled {

        @Autowired
        MockMvcTester mvc;

        @Autowired
        ApplicationContext context;

        @Test
        void contextStartsWithoutSwitchboardBeans() {
            assertThat(context.getBeanNamesForType(SwitchboardDispatcher.class)).isEmpty();
            assertThat(context.getBeanNamesForType(SwitchboardRegistry.class)).isEmpty();
        }

        @Test
        void refundEndpointIsAbsent() {
            assertThat(refund(mvc, "STANDARD", "{\"amount\":50}")).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    /** (e) Bad configuration stops startup, naming the group and the unknown resolver (SC-006). */
    @Test
    void unknownResolverInResolutionOrderStopsStartup() {
        Throwable failure = catchThrowable(() -> new SpringApplicationBuilder(SampleApplication.class)
                .properties("server.port=0", "switchboard.groups.refunds.resolution-order=does-not-exist")
                .run()
                .close());

        assertThat(failure).isNotNull();
        assertThat(NestedExceptionUtils.getMostSpecificCause(failure))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'refunds'")
                .hasMessageContaining("'does-not-exist'");
    }
}
