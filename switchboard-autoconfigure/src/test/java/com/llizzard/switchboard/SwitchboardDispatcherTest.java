package com.llizzard.switchboard;

import static com.llizzard.switchboard.Stubs.PAYMENTS;
import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.REFUNDS_OTHER_TYPES;
import static com.llizzard.switchboard.Stubs.resolver;
import static com.llizzard.switchboard.Stubs.strategy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import com.llizzard.switchboard.Stubs.Req;
import com.llizzard.switchboard.Stubs.Res;
import com.llizzard.switchboard.Stubs.StubResolver;
import com.llizzard.switchboard.Stubs.StubStrategy;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties;

@ExtendWith(OutputCaptureExtension.class)
class SwitchboardDispatcherTest {

    private static final SwitchboardProperties NO_CONFIG = new SwitchboardProperties(true, null);
    private static final String LOGGER = SwitchboardDispatcher.class.getName();
    private static final String SECRET = "SECRET-PAYLOAD-MARKER";

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());
    private final MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/refunds");
    private final Req payload = new Req(SECRET);

    @BeforeEach
    void enableDebugLogging() {
        loggingSystem.setLogLevel(LOGGER, LogLevel.DEBUG);
    }

    @AfterEach
    void resetLogging() {
        loggingSystem.setLogLevel(LOGGER, null);
    }

    private static SwitchboardDispatcher dispatcher(List<SwitchboardResolver<?>> resolvers,
            List<SwitchboardStrategy<?, ?>> strategies) {
        return new SwitchboardDispatcher(
                new SwitchboardResolverChain(resolvers, NO_CONFIG),
                new SwitchboardRegistry(strategies, NO_CONFIG));
    }

    private static List<String> dispatchLines(CapturedOutput output) {
        return Arrays.stream(output.getOut().split("\\R"))
                .filter(line -> line.contains(LOGGER) || line.contains("Switchboard dispatch"))
                .toList();
    }

    @Test
    void happyPathReturnsResponseAndPassesContext() {
        StubStrategy<Req, Res> standard = strategy(REFUNDS, "STANDARD");
        SwitchboardDispatcher dispatcher = dispatcher(
                List.of(resolver(REFUNDS, "refund-type-header", "STANDARD")), List.of(standard));

        Res response = dispatcher.dispatch(REFUNDS, http, payload);

        assertThat(response).isEqualTo(new Res("STANDARD"));
        assertThat(standard.lastRequest).hasValue(payload);
        assertThat(standard.lastContext)
                .hasValue(new SwitchboardContext("refunds", "STANDARD", "refund-type-header"));
    }

    @Test
    void emptyResolutionThrowsUnresolved() {
        SwitchboardDispatcher dispatcher = dispatcher(
                List.of(resolver(REFUNDS, "a", null)), List.of(strategy(REFUNDS, "STANDARD")));

        assertThatExceptionOfType(SwitchboardUnresolvedException.class)
                .isThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload))
                .satisfies(ex -> assertThat(ex.group()).isEqualTo("refunds"));
    }

    @Test
    void resolvedKeyWithoutStrategyThrowsNotAvailable() {
        SwitchboardDispatcher dispatcher = dispatcher(
                List.of(resolver(REFUNDS, "a", "NOPE")), List.of(strategy(REFUNDS, "STANDARD")));

        assertThatExceptionOfType(SwitchboardNotAvailableException.class)
                .isThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload))
                .satisfies(ex -> {
                    assertThat(ex.group()).isEqualTo("refunds");
                    assertThat(ex.caseKey()).isEqualTo("NOPE");
                });
    }

    @Test
    void keyFromAnotherGroupNeverRunsThatGroupsStrategy() {
        StubStrategy<Req, Res> card = strategy(PAYMENTS, "CARD");
        SwitchboardDispatcher dispatcher = dispatcher(
                List.of(resolver(REFUNDS, "a", "CARD"), resolver(PAYMENTS, "p", "CARD")),
                List.of(strategy(REFUNDS, "STANDARD"), card));

        assertThatExceptionOfType(SwitchboardNotAvailableException.class)
                .isThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload));
        assertThat(card.calls).hasValue(0);
    }

    @Test
    void groupWithResolversButNoStrategiesGives404WhenKeyResolved() {
        SwitchboardDispatcher dispatcher = dispatcher(List.of(resolver(REFUNDS, "a", "STANDARD")), List.of());

        assertThatExceptionOfType(SwitchboardNotAvailableException.class)
                .isThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload));
    }

    @Test
    void groupWithResolversButNoStrategiesGives501WhenNothingResolved() {
        SwitchboardDispatcher dispatcher = dispatcher(List.of(resolver(REFUNDS, "a", null)), List.of());

        assertThatExceptionOfType(SwitchboardUnresolvedException.class)
                .isThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload));
    }

    @Test
    void strategyExceptionPropagatesAsSameInstance() {
        IllegalStateException boom = new IllegalStateException("boom");
        StubStrategy<Req, Res> failing = new StubStrategy<>(REFUNDS, "BOOM", (r, c) -> {
            throw boom;
        });
        SwitchboardDispatcher dispatcher = dispatcher(List.of(resolver(REFUNDS, "a", "BOOM")), List.of(failing));

        assertThatThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload)).isSameAs(boom);
    }

    @Test
    void nullPayloadIsAllowed() {
        StubStrategy<Req, Res> standard = strategy(REFUNDS, "STANDARD");
        SwitchboardDispatcher dispatcher = dispatcher(List.of(resolver(REFUNDS, "a", "STANDARD")), List.of(standard));

        assertThat(dispatcher.dispatch(REFUNDS, http, null)).isEqualTo(new Res("STANDARD"));
        assertThat(standard.lastRequest).hasValue(null);
    }

    @Test
    void nullGroupOrHttpRequestFails() {
        SwitchboardDispatcher dispatcher = dispatcher(List.of(), List.of());

        assertThatNullPointerException()
                .isThrownBy(() -> dispatcher.dispatch(null, http, payload))
                .withMessageContaining("group");
        assertThatNullPointerException()
                .isThrownBy(() -> dispatcher.dispatch(REFUNDS, null, payload))
                .withMessageContaining("httpRequest");
    }

    @Test
    void nullConstructorArgumentsFail() {
        SwitchboardRegistry registry = new SwitchboardRegistry(List.of(), NO_CONFIG);
        SwitchboardResolverChain chain = new SwitchboardResolverChain(List.of(), NO_CONFIG);

        assertThatNullPointerException()
                .isThrownBy(() -> new SwitchboardDispatcher(null, registry))
                .withMessageContaining("chain");
        assertThatNullPointerException()
                .isThrownBy(() -> new SwitchboardDispatcher(chain, null))
                .withMessageContaining("registry");
    }

    @Test
    void strategyRegisteredUnderSameNameWithDifferentTypesIsNotCastBlindly() {
        // A registry built on its own (no startup validator) holding a foreign-typed strategy
        // under the dispatched group's name: the dispatcher refuses rather than casting.
        StubStrategy<String, Res> foreign = new StubStrategy<>(REFUNDS_OTHER_TYPES, "STANDARD", (r, c) -> new Res("x"));
        SwitchboardDispatcher dispatcher = new SwitchboardDispatcher(
                new SwitchboardResolverChain(List.of(resolver(REFUNDS, "a", "STANDARD")), NO_CONFIG),
                new SwitchboardRegistry(List.of(foreign), NO_CONFIG));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload))
                .withMessageContaining("'refunds'");
        assertThat(foreign.calls).hasValue(0);
    }

    // --- Logging (FR-027) ---

    @Test
    void handledOutcomeLogsOneLine(CapturedOutput output) {
        dispatcher(List.of(resolver(REFUNDS, "refund-type-header", "STANDARD")), List.of(strategy(REFUNDS, "STANDARD")))
                .dispatch(REFUNDS, http, payload);

        assertThat(dispatchLines(output)).singleElement().asString()
                .contains("DEBUG", "group=refunds", "key=STANDARD", "resolver=refund-type-header",
                        "outcome=handled", "elapsedMicros=");
    }

    @Test
    void notFoundOutcomeLogsOneLine(CapturedOutput output) {
        SwitchboardDispatcher dispatcher = dispatcher(List.of(resolver(REFUNDS, "a", "NOPE")), List.of());

        assertThatThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload));

        assertThat(dispatchLines(output)).singleElement().asString()
                .contains("group=refunds", "key=NOPE", "resolver=a", "outcome=not_found", "elapsedMicros=");
    }

    @Test
    void notImplementedOutcomeLogsOneLine(CapturedOutput output) {
        SwitchboardDispatcher dispatcher = dispatcher(List.of(resolver(REFUNDS, "a", null)), List.of());

        assertThatThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload));

        assertThat(dispatchLines(output)).singleElement().asString()
                .contains("group=refunds", "key=-", "resolver=-", "outcome=not_implemented", "elapsedMicros=");
    }

    @Test
    void failedOutcomeLogsOneLineWhenStrategyThrows(CapturedOutput output) {
        StubStrategy<Req, Res> failing = new StubStrategy<>(REFUNDS, "BOOM", (r, c) -> {
            throw new IllegalStateException("boom " + r);
        });
        SwitchboardDispatcher dispatcher = dispatcher(List.of(resolver(REFUNDS, "a", "BOOM")), List.of(failing));

        assertThatThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload));

        assertThat(dispatchLines(output)).singleElement().asString()
                .contains("group=refunds", "key=BOOM", "resolver=a", "outcome=failed", "elapsedMicros=");
    }

    @Test
    void failedOutcomeLogsOneLineWhenResolverThrows(CapturedOutput output) {
        StubResolver<Req> failing = new StubResolver<>(REFUNDS, "explosive", (r, p) -> {
            throw new IllegalStateException("boom");
        });
        SwitchboardDispatcher dispatcher = dispatcher(List.of(failing), List.of());

        assertThatThrownBy(() -> dispatcher.dispatch(REFUNDS, http, payload));

        assertThat(dispatchLines(output)).singleElement().asString()
                .contains("group=refunds", "key=-", "resolver=-", "outcome=failed", "elapsedMicros=");
    }

    @Test
    void noLogLineContainsPayloadContents(CapturedOutput output) {
        StubStrategy<Req, Res> failing = new StubStrategy<>(REFUNDS, "BOOM", (r, c) -> {
            throw new IllegalStateException("boom " + r);
        });
        SwitchboardDispatcher dispatcher = dispatcher(
                List.of(new StubResolver<>(REFUNDS, "a",
                        (r, p) -> Optional.ofNullable(r.getHeader("X-Key")))),
                List.of(strategy(REFUNDS, "STANDARD"), failing));

        dispatcher.dispatch(REFUNDS, withKey("STANDARD"), payload);
        assertThatThrownBy(() -> dispatcher.dispatch(REFUNDS, withKey("NOPE"), payload));
        assertThatThrownBy(() -> dispatcher.dispatch(REFUNDS, withKey(null), payload));
        assertThatThrownBy(() -> dispatcher.dispatch(REFUNDS, withKey("BOOM"), payload));

        assertThat(dispatchLines(output)).hasSize(4);
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test
    void nothingIsLoggedWhenDebugIsOff(CapturedOutput output) {
        loggingSystem.setLogLevel(LOGGER, LogLevel.INFO);

        dispatcher(List.of(resolver(REFUNDS, "a", "STANDARD")), List.of(strategy(REFUNDS, "STANDARD")))
                .dispatch(REFUNDS, http, payload);

        assertThat(dispatchLines(output)).isEmpty();
    }

    private static MockHttpServletRequest withKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/refunds");
        if (key != null) {
            request.addHeader("X-Key", key);
        }
        return request;
    }
}
