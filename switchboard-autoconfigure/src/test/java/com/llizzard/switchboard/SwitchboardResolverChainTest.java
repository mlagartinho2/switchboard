package com.llizzard.switchboard;

import static com.llizzard.switchboard.Stubs.PAYMENTS;
import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.REFUNDS_OTHER_TYPES;
import static com.llizzard.switchboard.Stubs.resolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.llizzard.switchboard.Stubs.Req;
import com.llizzard.switchboard.Stubs.Res;
import com.llizzard.switchboard.Stubs.StubResolver;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.GroupSettings;

class SwitchboardResolverChainTest {

    private static final SwitchboardProperties NO_CONFIG = new SwitchboardProperties(true, null);

    private final MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/refunds");
    private final Req payload = new Req("p");

    /** Named classes so V3 messages can be checked for both class names. */
    static final class FirstH extends StubResolver<Req> {
        FirstH() {
            super(REFUNDS, "h", (r, p) -> Optional.empty());
        }
    }

    static final class SecondH extends StubResolver<Req> {
        SecondH() {
            super(REFUNDS, "h", (r, p) -> Optional.empty());
        }
    }

    private static SwitchboardResolverChain chain(SwitchboardResolver<?>... resolvers) {
        return new SwitchboardResolverChain(List.of(resolvers), NO_CONFIG);
    }

    @Test
    void firstNonEmptyResultWinsInGivenOrder() {
        SwitchboardResolverChain chain = chain(
                resolver(REFUNDS, "a", null),
                resolver(REFUNDS, "b", "STANDARD"),
                resolver(REFUNDS, "c", "EXPEDITED"));

        assertThat(chain.resolve(REFUNDS, http, payload))
                .contains(new SwitchboardResolution("STANDARD", "b"));
    }

    @Test
    void laterResolversAreNotInvokedOnceOneAnswers() {
        StubResolver<Req> a = resolver(REFUNDS, "a", "STANDARD");
        StubResolver<Req> b = resolver(REFUNDS, "b", "EXPEDITED");

        chain(a, b).resolve(REFUNDS, http, payload);

        assertThat(a.calls).hasValue(1);
        assertThat(b.calls).hasValue(0);
    }

    @Test
    void blankAndEmptyResultsCountAsNoKey() {
        StubResolver<Req> blank = resolver(REFUNDS, "blank", "  ");
        StubResolver<Req> empty = resolver(REFUNDS, "empty", "");
        StubResolver<Req> real = resolver(REFUNDS, "real", "EXPEDITED");

        assertThat(chain(blank, empty, real).resolve(REFUNDS, http, payload))
                .contains(new SwitchboardResolution("EXPEDITED", "real"));
        assertThat(blank.calls).hasValue(1);
        assertThat(empty.calls).hasValue(1);
    }

    @Test
    void keyIsPassedThroughUnchanged() {
        assertThat(chain(resolver(REFUNDS, "a", "MiXeD_key")).resolve(REFUNDS, http, payload))
                .contains(new SwitchboardResolution("MiXeD_key", "a"));
    }

    @Test
    void allEmptyGivesEmpty() {
        assertThat(chain(resolver(REFUNDS, "a", null), resolver(REFUNDS, "b", " "))
                .resolve(REFUNDS, http, payload)).isEmpty();
    }

    @Test
    void unknownGroupGivesEmpty() {
        assertThat(chain(resolver(REFUNDS, "a", "STANDARD")).resolve(PAYMENTS, http, payload)).isEmpty();
    }

    @Test
    void onlyResolversOfTheRequestedGroupAreConsulted() {
        StubResolver<Req> paymentsResolver = resolver(PAYMENTS, "p", "CARD");
        StubResolver<Req> refundsResolver = resolver(REFUNDS, "r", null);

        assertThat(chain(paymentsResolver, refundsResolver).resolve(REFUNDS, http, payload)).isEmpty();
        assertThat(paymentsResolver.calls).hasValue(0);
        assertThat(refundsResolver.calls).hasValue(1);
    }

    @Test
    void resolverReceivesTheRequestAndPayload() {
        StubResolver<Req> echo = new StubResolver<>(REFUNDS, "echo",
                (r, p) -> Optional.of(r.getHeader("X-Key") + "_" + p.value()));
        http.addHeader("X-Key", "K");

        assertThat(chain(echo).resolve(REFUNDS, http, payload))
                .contains(new SwitchboardResolution("K_p", "echo"));
    }

    @Test
    void nullPayloadIsPassedThrough() {
        StubResolver<Req> nullAware = new StubResolver<>(REFUNDS, "n",
                (r, p) -> p == null ? Optional.of("NULL_PAYLOAD") : Optional.empty());

        assertThat(chain(nullAware).resolve(REFUNDS, http, null))
                .contains(new SwitchboardResolution("NULL_PAYLOAD", "n"));
    }

    @Test
    void resolverExceptionPropagatesUnchanged() {
        IllegalStateException boom = new IllegalStateException("boom");
        StubResolver<Req> failing = new StubResolver<>(REFUNDS, "f", (r, p) -> {
            throw boom;
        });

        assertThatThrownBy(() -> chain(failing).resolve(REFUNDS, http, payload)).isSameAs(boom);
    }

    @Test
    void v3DuplicateNameInGroupFailsNamingGroupAndBothResolvers() {
        assertThatIllegalStateException()
                .isThrownBy(() -> chain(new FirstH(), new SecondH()))
                .withMessageContaining("'refunds'")
                .withMessageContaining("'h'")
                .withMessageContaining(FirstH.class.getName())
                .withMessageContaining(SecondH.class.getName());
    }

    @Test
    void sameNameInDifferentGroupsIsAllowed() {
        SwitchboardResolverChain chain = chain(resolver(REFUNDS, "h", "STANDARD"), resolver(PAYMENTS, "h", "CARD"));

        assertThat(chain.resolve(REFUNDS, http, payload)).contains(new SwitchboardResolution("STANDARD", "h"));
        assertThat(chain.resolve(PAYMENTS, http, payload)).contains(new SwitchboardResolution("CARD", "h"));
    }

    @Test
    void blankResolverNameFails() {
        assertThatIllegalStateException()
                .isThrownBy(() -> chain(resolver(REFUNDS, " ", "STANDARD")))
                .withMessageContaining("'refunds'");
    }

    @Test
    void nullGroupFromResolverFails() {
        assertThatIllegalStateException().isThrownBy(() -> chain(resolver(null, "a", "STANDARD")));
    }

    @Test
    void v8SameGroupNameWithDifferentTypesFailsNamingTheGroup() {
        StubResolver<String> otherTypes =
                new StubResolver<>(REFUNDS_OTHER_TYPES, "b", (r, p) -> Optional.empty());

        assertThatIllegalStateException()
                .isThrownBy(() -> chain(resolver(REFUNDS, "a", null), otherTypes))
                .withMessageContaining("'refunds'");
    }

    @Test
    void resolvingWithAHandleWhoseTypesDifferFromTheRegisteredOneFails() {
        SwitchboardGroup<String, Res> foreign = REFUNDS_OTHER_TYPES;

        assertThatIllegalArgumentException()
                .isThrownBy(() -> chain(resolver(REFUNDS, "a", "STANDARD")).resolve(foreign, http, "x"))
                .withMessageContaining("'refunds'");
    }

    @Test
    void resolutionOrderReturnsEffectiveNamesInOrder() {
        SwitchboardResolverChain chain = chain(
                resolver(REFUNDS, "refund-type-header", null),
                resolver(PAYMENTS, "payment-method-header", null),
                resolver(REFUNDS, "refund-amount", null));

        assertThat(chain.resolutionOrder("refunds")).containsExactly("refund-type-header", "refund-amount");
        assertThat(chain.resolutionOrder("payments")).containsExactly("payment-method-header");
        assertThat(chain.resolutionOrder("ghost")).isEmpty();
    }

    @Test
    void groupsReturnsGroupNames() {
        assertThat(chain(resolver(REFUNDS, "a", null), resolver(PAYMENTS, "b", null)).groups())
                .containsExactlyInAnyOrder("refunds", "payments");
    }

    @Test
    void laterMutationOfInputListDoesNotAffectChain() {
        List<SwitchboardResolver<?>> input = new ArrayList<>();
        input.add(resolver(REFUNDS, "a", null));
        SwitchboardResolverChain chain = new SwitchboardResolverChain(input, NO_CONFIG);

        input.add(resolver(REFUNDS, "b", "STANDARD"));

        assertThat(chain.resolutionOrder("refunds")).containsExactly("a");
        assertThat(chain.resolve(REFUNDS, http, payload)).isEmpty();
    }

    @Test
    void nullArgumentsFailWithMessage() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SwitchboardResolverChain(null, NO_CONFIG))
                .withMessageContaining("orderedResolvers");
        assertThatNullPointerException()
                .isThrownBy(() -> new SwitchboardResolverChain(List.of(), null))
                .withMessageContaining("properties");
        SwitchboardResolverChain chain = chain();
        assertThatNullPointerException()
                .isThrownBy(() -> chain.resolve(null, http, payload))
                .withMessageContaining("group");
        assertThatNullPointerException()
                .isThrownBy(() -> chain.resolve(REFUNDS, null, payload))
                .withMessageContaining("httpRequest");
    }

    // --- US3: configured resolution-order (FR-019, V4, V5) ---

    private static SwitchboardProperties order(String group, List<String> resolutionOrder) {
        return new SwitchboardProperties(true, Map.of(group, new GroupSettings(resolutionOrder, null)));
    }

    @Test
    void configuredOrderOverridesTheGivenOrder() {
        StubResolver<Req> a = resolver(REFUNDS, "a", "STANDARD");
        StubResolver<Req> b = resolver(REFUNDS, "b", "EXPEDITED");
        SwitchboardResolverChain chain =
                new SwitchboardResolverChain(List.of(a, b), order("refunds", List.of("b", "a")));

        assertThat(chain.resolve(REFUNDS, http, payload)).contains(new SwitchboardResolution("EXPEDITED", "b"));
        assertThat(a.calls).hasValue(0);
    }

    @Test
    void configuredOrderConsultsOnlyTheNamedResolvers() {
        StubResolver<Req> a = resolver(REFUNDS, "a", null);
        StubResolver<Req> b = resolver(REFUNDS, "b", "EXPEDITED");
        SwitchboardResolverChain chain = new SwitchboardResolverChain(List.of(a, b), order("refunds", List.of("a")));

        assertThat(chain.resolve(REFUNDS, http, payload)).isEmpty();
        assertThat(a.calls).hasValue(1);
        assertThat(b.calls).hasValue(0);
    }

    @Test
    void v4UnknownNameInConfiguredOrderFailsNamingGroupAndName() {
        List<SwitchboardResolver<?>> resolvers = List.of(resolver(REFUNDS, "a", null));

        assertThatIllegalStateException()
                .isThrownBy(() -> new SwitchboardResolverChain(resolvers, order("refunds", List.of("a", "zzz"))))
                .withMessageContaining("'refunds'")
                .withMessageContaining("'zzz'")
                .withMessageContaining("[a]");
    }

    @Test
    void v4NameOfAResolverInAnotherGroupIsUnknown() {
        List<SwitchboardResolver<?>> resolvers = List.of(resolver(REFUNDS, "a", null), resolver(PAYMENTS, "p", null));

        assertThatIllegalStateException()
                .isThrownBy(() -> new SwitchboardResolverChain(resolvers, order("refunds", List.of("p"))))
                .withMessageContaining("'refunds'")
                .withMessageContaining("'p'");
    }

    @Test
    void v4ConfiguredOrderForAGroupWithoutResolversFails() {
        List<SwitchboardResolver<?>> resolvers = List.of(resolver(REFUNDS, "a", null));

        assertThatIllegalStateException()
                .isThrownBy(() -> new SwitchboardResolverChain(resolvers, order("ghost", List.of("g"))))
                .withMessageContaining("'ghost'")
                .withMessageContaining("'g'");
    }

    @Test
    void v5EmptyConfiguredOrderFailsNamingTheGroup() {
        List<SwitchboardResolver<?>> resolvers = List.of(resolver(REFUNDS, "a", null));

        assertThatIllegalStateException()
                .isThrownBy(() -> new SwitchboardResolverChain(resolvers, order("refunds", List.of())))
                .withMessageContaining("'refunds'")
                .withMessageContaining("empty");
    }

    @Test
    void absentConfiguredOrderFallsBackToTheGivenOrder() {
        SwitchboardResolverChain chain = new SwitchboardResolverChain(
                List.of(resolver(REFUNDS, "a", null), resolver(REFUNDS, "b", "STANDARD")), order("refunds", null));

        assertThat(chain.resolutionOrder("refunds")).containsExactly("a", "b");
        assertThat(chain.resolve(REFUNDS, http, payload)).contains(new SwitchboardResolution("STANDARD", "b"));
    }

    @Test
    void resolutionOrderReportsTheConfiguredOrder() {
        SwitchboardResolverChain chain = new SwitchboardResolverChain(
                List.of(resolver(REFUNDS, "a", null), resolver(REFUNDS, "b", null), resolver(REFUNDS, "c", null)),
                order("refunds", List.of("c", "a")));

        assertThat(chain.resolutionOrder("refunds")).containsExactly("c", "a");
    }

    @Test
    void configuredOrderInOneGroupDoesNotAffectAnother() {
        SwitchboardResolverChain chain = new SwitchboardResolverChain(
                List.of(resolver(REFUNDS, "a", null), resolver(REFUNDS, "b", null),
                        resolver(PAYMENTS, "x", null), resolver(PAYMENTS, "y", null)),
                order("refunds", List.of("b")));

        assertThat(chain.resolutionOrder("refunds")).containsExactly("b");
        assertThat(chain.resolutionOrder("payments")).containsExactly("x", "y");
    }
}
