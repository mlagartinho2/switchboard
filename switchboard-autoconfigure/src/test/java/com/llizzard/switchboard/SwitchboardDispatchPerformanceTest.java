package com.llizzard.switchboard;

import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.resolver;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.llizzard.switchboard.Stubs.Req;
import com.llizzard.switchboard.Stubs.Res;
import com.llizzard.switchboard.Stubs.StubStrategy;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties;

/**
 * SC-008: the framework adds less than 1 ms at p95 per dispatch (§R12). A plain timer, not JMH
 * (Principle VII): the bound is about three orders of magnitude above the expected cost, so the
 * check is stable without a benchmarking harness.
 */
class SwitchboardDispatchPerformanceTest {

    private static final int WARM_UP = 10_000;
    private static final int MEASURED = 100_000;
    private static final long P95_LIMIT_NANOS = 1_000_000L;

    @Test
    void p95DispatchOverheadIsBelowOneMillisecond() {
        SwitchboardProperties properties = new SwitchboardProperties(true, null);
        Res constant = new Res("STANDARD");
        SwitchboardRegistry registry = new SwitchboardRegistry(
                List.of(new StubStrategy<Req, Res>(REFUNDS, "STANDARD", (r, c) -> constant)), properties);
        SwitchboardResolverChain chain = new SwitchboardResolverChain(List.of(
                resolver(REFUNDS, "first", null),
                resolver(REFUNDS, "second", null),
                resolver(REFUNDS, "third", "STANDARD")), properties);
        SwitchboardDispatcher dispatcher = new SwitchboardDispatcher(chain, registry);
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/refunds");
        Req payload = new Req("p");

        for (int i = 0; i < WARM_UP; i++) {
            dispatcher.dispatch(REFUNDS, http, payload);
        }
        long[] samples = new long[MEASURED];
        Res last = null;
        for (int i = 0; i < MEASURED; i++) {
            long start = System.nanoTime();
            last = dispatcher.dispatch(REFUNDS, http, payload);
            samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        long p95 = samples[(int) Math.ceil(MEASURED * 0.95) - 1];

        assertThat(last).isSameAs(constant); // keeps the calls observable
        assertThat(p95).as("p95 dispatch time in ns (median %d ns)", samples[MEASURED / 2])
                .isLessThan(P95_LIMIT_NANOS);
    }
}
