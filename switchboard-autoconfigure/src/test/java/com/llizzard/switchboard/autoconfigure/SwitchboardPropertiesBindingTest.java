package com.llizzard.switchboard.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.CaseSettings;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.GroupSettings;

class SwitchboardPropertiesBindingTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SwitchboardProperties.class)
    static class PropertiesConfig {
    }

    private void bind(Consumer<SwitchboardProperties> assertions, String... properties) {
        runner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasNotFailed();
            assertions.accept(context.getBean(SwitchboardProperties.class));
        });
    }

    @Test
    void enabledDefaultsToTrueAndGroupsToEmpty() {
        bind(props -> {
            assertThat(props.enabled()).isTrue();
            assertThat(props.groups()).isEmpty();
        });
    }

    @Test
    void enabledCanBeTurnedOff() {
        bind(props -> assertThat(props.enabled()).isFalse(), "switchboard.enabled=false");
    }

    @Test
    void absentResolutionOrderBindsToNull() {
        bind(props -> assertThat(props.groups().get("refunds").resolutionOrder()).isNull(),
                "switchboard.groups.refunds.cases.[STANDARD].owner=payments-team");
    }

    @Test
    void presentResolutionOrderBindsInOrder() {
        bind(props -> assertThat(props.groups().get("refunds").resolutionOrder()).containsExactly("a", "b"),
                "switchboard.groups.refunds.resolution-order[0]=a",
                "switchboard.groups.refunds.resolution-order[1]=b");
    }

    @Test
    void singleEntryResolutionOrderBinds() {
        bind(props -> assertThat(props.groups().get("refunds").resolutionOrder()).containsExactly("a"),
                "switchboard.groups.refunds.resolution-order[0]=a");
    }

    @Test
    void bracketedCaseKeyKeepsCaseAndUnderscore() {
        bind(props -> {
            GroupSettings refunds = props.groups().get("refunds");
            assertThat(refunds.cases()).containsOnlyKeys("REFUND_STANDARD");
            assertThat(refunds.cases().get("REFUND_STANDARD").enabled()).isFalse();
        }, "switchboard.groups.refunds.cases.[REFUND_STANDARD].enabled=false");
    }

    @Test
    void caseSettingsDefaults() {
        bind(props -> {
            CaseSettings standard = props.groups().get("refunds").cases().get("STANDARD");
            assertThat(standard.enabled()).isTrue();
            assertThat(standard.owner()).isNull();
            assertThat(standard.version()).isNull();
            assertThat(standard.description()).isNull();
        }, "switchboard.groups.refunds.cases.[STANDARD].enabled=true");
    }

    @Test
    void caseEnabledDefaultsToTrueWhenOnlyMetadataIsSet() {
        bind(props -> {
            CaseSettings standard = props.groups().get("refunds").cases().get("STANDARD");
            assertThat(standard.enabled()).isTrue();
            assertThat(standard.owner()).isEqualTo("payments-team");
            assertThat(standard.version()).isEqualTo("1.0");
            assertThat(standard.description()).isEqualTo("Standard refund flow");
        },
                "switchboard.groups.refunds.cases.[STANDARD].owner=payments-team",
                "switchboard.groups.refunds.cases.[STANDARD].version=1.0",
                "switchboard.groups.refunds.cases.[STANDARD].description=Standard refund flow");
    }

    @Test
    void groupWithOnlyResolutionOrderHasEmptyCases() {
        bind(props -> assertThat(props.groups().get("refunds").cases()).isEmpty(),
                "switchboard.groups.refunds.resolution-order[0]=a");
    }

    @Test
    void boundCollectionsAreUnmodifiable() {
        bind(props -> {
            GroupSettings refunds = props.groups().get("refunds");
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> props.groups().put("other", new GroupSettings(null, null)));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> refunds.cases().clear());
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> refunds.resolutionOrder().add("c"));
        },
                "switchboard.groups.refunds.resolution-order[0]=a",
                "switchboard.groups.refunds.cases.[STANDARD].enabled=false");
    }

    @Test
    void constructorTakesDefensiveCopies() {
        List<String> order = new ArrayList<>(List.of("a"));
        GroupSettings settings = new GroupSettings(order, null);
        order.add("b");

        assertThat(settings.resolutionOrder()).containsExactly("a");
        assertThat(settings.cases()).isEmpty();
    }

    @Test
    void groupLookupReturnsEmptySettingsForUnknownGroup() {
        SwitchboardProperties props = new SwitchboardProperties(true, null);

        GroupSettings unknown = props.group("unknown");
        assertThat(unknown).isNotNull();
        assertThat(unknown.resolutionOrder()).isNull();
        assertThat(unknown.cases()).isEmpty();
    }

    @Test
    void isEnabledDefaultsToTrueAndHonoursKillSwitch() {
        bind(props -> {
            assertThat(props.isEnabled("refunds", "EXPEDITED")).isFalse();
            assertThat(props.isEnabled("refunds", "STANDARD")).isTrue();
            assertThat(props.isEnabled("refunds", "UNCONFIGURED")).isTrue();
            assertThat(props.isEnabled("payments", "STANDARD")).isTrue();
        },
                "switchboard.groups.refunds.cases.[EXPEDITED].enabled=false",
                "switchboard.groups.refunds.cases.[STANDARD].owner=payments-team");
    }

    @Test
    void sameKeyInDifferentGroupsIsIndependent() {
        bind(props -> {
            assertThat(props.isEnabled("refunds", "STANDARD")).isFalse();
            assertThat(props.isEnabled("payments", "STANDARD")).isTrue();
        },
                "switchboard.groups.refunds.cases.[STANDARD].enabled=false",
                "switchboard.groups.payments.cases.[STANDARD].enabled=true");
    }
}
