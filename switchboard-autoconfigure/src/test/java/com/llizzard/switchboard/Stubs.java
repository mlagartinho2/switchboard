package com.llizzard.switchboard;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import jakarta.servlet.http.HttpServletRequest;

/** Test-only groups, strategies, and resolvers shared by the unit and auto-configuration tests. */
public final class Stubs {

    public record Req(String value) {
        @Override
        public String toString() {
            return "Req[" + value + "]";
        }
    }

    public record Res(String handledBy) {
    }

    public static final SwitchboardGroup<Req, Res> REFUNDS = SwitchboardGroup.of("refunds", Req.class, Res.class);
    public static final SwitchboardGroup<Req, Res> PAYMENTS = SwitchboardGroup.of("payments", Req.class, Res.class);
    /** Same name as {@link #REFUNDS}, different request type: a V8 violation when mixed with it. */
    public static final SwitchboardGroup<String, Res> REFUNDS_OTHER_TYPES =
            SwitchboardGroup.of("refunds", String.class, Res.class);

    private Stubs() {
    }

    public static StubStrategy<Req, Res> strategy(SwitchboardGroup<Req, Res> group, String key) {
        return new StubStrategy<>(group, key, (request, context) -> new Res(key));
    }

    public static StubResolver<Req> resolver(SwitchboardGroup<Req, ?> group, String name, String key) {
        return new StubResolver<>(group, name, (http, payload) -> Optional.ofNullable(key));
    }

    public static class StubStrategy<Q, S> implements SwitchboardStrategy<Q, S> {

        private final SwitchboardGroup<Q, S> group;
        private final String key;
        private final BiFunction<Q, SwitchboardContext, S> body;
        public final AtomicInteger calls = new AtomicInteger();
        public final AtomicReference<SwitchboardContext> lastContext = new AtomicReference<>();
        public final AtomicReference<Q> lastRequest = new AtomicReference<>();

        public StubStrategy(SwitchboardGroup<Q, S> group, String key, BiFunction<Q, SwitchboardContext, S> body) {
            this.group = group;
            this.key = key;
            this.body = body;
        }

        @Override
        public SwitchboardGroup<Q, S> group() {
            return group;
        }

        @Override
        public String caseKey() {
            return key;
        }

        @Override
        public S execute(Q request, SwitchboardContext context) {
            calls.incrementAndGet();
            lastRequest.set(request);
            lastContext.set(context);
            return body.apply(request, context);
        }
    }

    public static class StubResolver<Q> implements SwitchboardResolver<Q> {

        private final SwitchboardGroup<Q, ?> group;
        private final String name;
        private final BiFunction<HttpServletRequest, Q, Optional<String>> body;
        public final AtomicInteger calls = new AtomicInteger();

        public StubResolver(SwitchboardGroup<Q, ?> group, String name,
                BiFunction<HttpServletRequest, Q, Optional<String>> body) {
            this.group = group;
            this.name = name;
            this.body = body;
        }

        @Override
        public SwitchboardGroup<Q, ?> group() {
            return group;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Optional<String> resolve(HttpServletRequest httpRequest, Q payload) {
            calls.incrementAndGet();
            return body.apply(httpRequest, payload);
        }
    }
}
