package com.llizzard.switchboard.actuate;

import static com.llizzard.switchboard.Stubs.PAYMENTS;
import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.resolver;
import static com.llizzard.switchboard.Stubs.strategy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.llizzard.switchboard.Stubs.Req;
import com.llizzard.switchboard.Stubs.Res;
import com.llizzard.switchboard.Stubs.StubResolver;
import com.llizzard.switchboard.Stubs.StubStrategy;
import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardRegistry;
import com.llizzard.switchboard.SwitchboardResolver;
import com.llizzard.switchboard.SwitchboardResolverChain;
import com.llizzard.switchboard.SwitchboardStrategy;
import com.llizzard.switchboard.actuate.SwitchboardReport.CaseReport;
import com.llizzard.switchboard.actuate.SwitchboardReport.GroupReport;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.CaseSettings;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.GroupSettings;

/** Plain JUnit: real registry and chain from stubs, rules from contracts/actuator-endpoint.md. */
class SwitchboardEndpointTest {

    private static final SwitchboardProperties NO_CONFIG = new SwitchboardProperties(true, null);

    /** Named like a Spring CGLIB proxy of {@link StandardStrategy}. */
    static class StandardStrategy extends StubStrategy<Req, Res> {
        StandardStrategy() {
            super(REFUNDS, "STANDARD", (r, c) -> new Res("standard"));
        }
    }

    static class StandardStrategy$$SpringCGLIB$$0 extends StandardStrategy {
    }

    private static SwitchboardReport report(List<SwitchboardStrategy<?, ?>> strategies,
            List<SwitchboardResolver<?>> resolvers, SwitchboardProperties properties) {
        return new SwitchboardEndpoint(new SwitchboardRegistry(strategies, properties),
                new SwitchboardResolverChain(resolvers, properties), properties).report();
    }

    private static CaseReport row(SwitchboardReport report, String group, String key) {
        return report.groups().get(group).cases().stream()
                .filter(c -> c.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row " + group + "/" + key));
    }

    @Test
    void coversEveryGroupWithAStrategyOrResolverSortedByName() {
        SwitchboardGroup<Req, Res> onlyResolver = SwitchboardGroup.of("alerts", Req.class, Res.class);
        SwitchboardReport report = report(
                List.of(strategy(REFUNDS, "STANDARD"), strategy(PAYMENTS, "CARD")),
                List.of(resolver(REFUNDS, "h", null), resolver(PAYMENTS, "p", null),
                        new StubResolver<>(onlyResolver, "a", (r, p) -> Optional.empty())),
                NO_CONFIG);

        assertThat(report.groups()).containsOnlyKeys("alerts", "payments", "refunds");
        assertThat(report.groups().keySet()).containsExactly("alerts", "payments", "refunds");
        assertThat(report.groups().get("alerts").cases()).isEmpty();
        assertThat(report.groups().get("alerts").resolutionOrder()).containsExactly("a");
    }

    @Test
    void resolutionOrderIsTheDefaultBeanOrderWhenNotConfigured() {
        SwitchboardReport report = report(List.of(strategy(REFUNDS, "STANDARD")),
                List.of(resolver(REFUNDS, "b", null), resolver(REFUNDS, "a", null)), NO_CONFIG);

        assertThat(report.groups().get("refunds").resolutionOrder()).containsExactly("b", "a");
    }

    @Test
    void resolutionOrderIsTheConfiguredOrderWhenPresent() {
        SwitchboardProperties configured = new SwitchboardProperties(true,
                Map.of("refunds", new GroupSettings(List.of("a"), null)));

        SwitchboardReport report = report(List.of(strategy(REFUNDS, "STANDARD")),
                List.of(resolver(REFUNDS, "b", null), resolver(REFUNDS, "a", null)), configured);

        assertThat(report.groups().get("refunds").resolutionOrder()).containsExactly("a");
    }

    @Test
    void casesAreTheUnionOfRegisteredAndConfiguredKeysSortedByKey() {
        SwitchboardProperties configured = new SwitchboardProperties(true, Map.of("refunds", new GroupSettings(null,
                Map.of("LEGACY", new CaseSettings(true, null, null, null),
                        "EXPEDITED", new CaseSettings(false, null, null, null)))));

        SwitchboardReport report = report(List.of(strategy(REFUNDS, "STANDARD"), strategy(REFUNDS, "EXPEDITED")),
                List.of(resolver(REFUNDS, "h", null)), configured);

        assertThat(report.groups().get("refunds").cases()).extracting(CaseReport::key)
                .containsExactly("EXPEDITED", "LEGACY", "STANDARD");
    }

    @Test
    void enabledAndRegisteredMatrix() {
        SwitchboardProperties configured = new SwitchboardProperties(true, Map.of("refunds", new GroupSettings(null,
                Map.of("OFF", new CaseSettings(false, null, null, null),
                        "PLANNED", new CaseSettings(true, "team", null, null),
                        "RETIRED", new CaseSettings(false, null, null, null)))));

        SwitchboardReport report = report(List.of(strategy(REFUNDS, "ON"), strategy(REFUNDS, "OFF")),
                List.of(resolver(REFUNDS, "h", null)), configured);

        // registered, no setting
        assertThat(row(report, "refunds", "ON")).extracting(CaseReport::enabled, CaseReport::registered)
                .containsExactly(true, true);
        // registered, enabled: false
        assertThat(row(report, "refunds", "OFF")).extracting(CaseReport::enabled, CaseReport::registered)
                .containsExactly(false, true);
        // not registered, configured (enabled absent/true)
        assertThat(row(report, "refunds", "PLANNED")).extracting(CaseReport::enabled, CaseReport::registered)
                .containsExactly(true, false);
        // not registered, enabled: false
        assertThat(row(report, "refunds", "RETIRED")).extracting(CaseReport::enabled, CaseReport::registered)
                .containsExactly(false, false);
        // not registered, not configured: not listed
        assertThat(report.groups().get("refunds").cases()).extracting(CaseReport::key)
                .containsExactly("OFF", "ON", "PLANNED", "RETIRED");
    }

    @Test
    void strategyClassIsTheUserClassOrNull() {
        SwitchboardProperties configured = new SwitchboardProperties(true, Map.of("refunds", new GroupSettings(null,
                Map.of("LEGACY", new CaseSettings(true, null, null, null)))));

        SwitchboardReport report = report(List.of(new StandardStrategy$$SpringCGLIB$$0()),
                List.of(resolver(REFUNDS, "h", null)), configured);

        assertThat(row(report, "refunds", "STANDARD").strategyClass()).isEqualTo(StandardStrategy.class.getName());
        assertThat(row(report, "refunds", "LEGACY").strategyClass()).isNull();
    }

    @Test
    void metadataComesFromConfigurationOrIsNull() {
        SwitchboardProperties configured = new SwitchboardProperties(true, Map.of("refunds", new GroupSettings(null,
                Map.of("STANDARD", new CaseSettings(true, "payments-team", "1.0", "Standard refund flow")))));

        SwitchboardReport report = report(List.of(strategy(REFUNDS, "STANDARD"), strategy(REFUNDS, "EXPEDITED")),
                List.of(resolver(REFUNDS, "h", null)), configured);

        assertThat(row(report, "refunds", "STANDARD"))
                .extracting(CaseReport::owner, CaseReport::version, CaseReport::description)
                .containsExactly("payments-team", "1.0", "Standard refund flow");
        assertThat(row(report, "refunds", "EXPEDITED"))
                .extracting(CaseReport::owner, CaseReport::version, CaseReport::description)
                .containsExactly(null, null, null);
    }

    @Test
    void reportIsImmutable() {
        SwitchboardReport report = report(List.of(strategy(REFUNDS, "STANDARD")),
                List.of(resolver(REFUNDS, "h", null)), NO_CONFIG);
        GroupReport refunds = report.groups().get("refunds");

        assertThat(report.groups()).isUnmodifiable();
        assertThat(refunds.cases()).isUnmodifiable();
        assertThat(refunds.resolutionOrder()).isUnmodifiable();
    }

    @Test
    void nullConstructorArgumentsFailWithMessage() {
        SwitchboardRegistry registry = new SwitchboardRegistry(List.of(), NO_CONFIG);
        SwitchboardResolverChain chain = new SwitchboardResolverChain(List.of(), NO_CONFIG);

        assertThatNullPointerException().isThrownBy(() -> new SwitchboardEndpoint(null, chain, NO_CONFIG))
                .withMessageContaining("registry");
        assertThatNullPointerException().isThrownBy(() -> new SwitchboardEndpoint(registry, null, NO_CONFIG))
                .withMessageContaining("chain");
        assertThatNullPointerException().isThrownBy(() -> new SwitchboardEndpoint(registry, chain, null))
                .withMessageContaining("properties");
    }
}
