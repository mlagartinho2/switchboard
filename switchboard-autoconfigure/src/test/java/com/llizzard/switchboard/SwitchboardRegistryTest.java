package com.llizzard.switchboard;

import static com.llizzard.switchboard.Stubs.PAYMENTS;
import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.REFUNDS_OTHER_TYPES;
import static com.llizzard.switchboard.Stubs.strategy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.llizzard.switchboard.Stubs.Req;
import com.llizzard.switchboard.Stubs.Res;
import com.llizzard.switchboard.Stubs.StubStrategy;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.CaseSettings;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.GroupSettings;

class SwitchboardRegistryTest {

    private static final SwitchboardProperties NO_CONFIG = new SwitchboardProperties(true, null);

    /** Named classes so V1 messages can be checked for both class names. */
    static final class FirstX extends StubStrategy<Req, Res> {
        FirstX() {
            super(REFUNDS, "X", (r, c) -> new Res("first"));
        }
    }

    static final class SecondX extends StubStrategy<Req, Res> {
        SecondX() {
            super(REFUNDS, "X", (r, c) -> new Res("second"));
        }
    }

    private static SwitchboardRegistry registry(SwitchboardStrategy<?, ?>... strategies) {
        return new SwitchboardRegistry(List.of(strategies), NO_CONFIG);
    }

    @Test
    void findReturnsTheRegisteredStrategy() {
        StubStrategy<Req, Res> standard = strategy(REFUNDS, "STANDARD");

        assertThat(registry(standard).find("refunds", "STANDARD")).containsSame(standard);
    }

    @Test
    void findReturnsEmptyForUnknownKey() {
        assertThat(registry(strategy(REFUNDS, "STANDARD")).find("refunds", "NOPE")).isEmpty();
    }

    @Test
    void findReturnsEmptyForKeyRegisteredOnlyInAnotherGroup() {
        SwitchboardRegistry registry = registry(strategy(REFUNDS, "STANDARD"), strategy(PAYMENTS, "CARD"));

        assertThat(registry.find("refunds", "CARD")).isEmpty();
        assertThat(registry.find("payments", "STANDARD")).isEmpty();
    }

    @Test
    void findReturnsEmptyForUnknownGroup() {
        assertThat(registry(strategy(REFUNDS, "STANDARD")).find("ghost", "STANDARD")).isEmpty();
    }

    @Test
    void keyMatchingIsExact() {
        SwitchboardRegistry registry = registry(strategy(REFUNDS, "STANDARD"));

        assertThat(registry.find("refunds", "standard")).isEmpty();
        assertThat(registry.find("refunds", " STANDARD")).isEmpty();
        assertThat(registry.find("refunds", "STANDARD ")).isEmpty();
    }

    @Test
    void registeredReturnsKeyToStrategyMap() {
        StubStrategy<Req, Res> standard = strategy(REFUNDS, "STANDARD");
        StubStrategy<Req, Res> expedited = strategy(REFUNDS, "EXPEDITED");
        SwitchboardRegistry registry = registry(standard, expedited, strategy(PAYMENTS, "CARD"));

        assertThat(registry.registered("refunds"))
                .containsOnlyKeys("STANDARD", "EXPEDITED")
                .containsEntry("STANDARD", standard)
                .containsEntry("EXPEDITED", expedited);
    }

    @Test
    void registeredReturnsEmptyMapForUnknownGroup() {
        assertThat(registry(strategy(REFUNDS, "STANDARD")).registered("ghost")).isEmpty();
    }

    @Test
    void groupsReturnsGroupNames() {
        SwitchboardRegistry registry = registry(strategy(REFUNDS, "STANDARD"), strategy(PAYMENTS, "CARD"));

        assertThat(registry.groups()).containsExactlyInAnyOrder("refunds", "payments");
    }

    @Test
    void emptyRegistryHasNoGroups() {
        assertThat(registry().groups()).isEmpty();
    }

    @Test
    void v1DuplicateKeyInGroupFailsNamingGroupKeyAndBothClasses() {
        assertThatIllegalStateException()
                .isThrownBy(() -> registry(new FirstX(), new SecondX()))
                .withMessageContaining("refunds")
                .withMessageContaining("'X'")
                .withMessageContaining(FirstX.class.getName())
                .withMessageContaining(SecondX.class.getName());
    }

    @Test
    void sameKeyInDifferentGroupsIsAllowed() {
        SwitchboardRegistry registry = registry(strategy(REFUNDS, "STANDARD"), strategy(PAYMENTS, "STANDARD"));

        assertThat(registry.find("refunds", "STANDARD")).isPresent();
        assertThat(registry.find("payments", "STANDARD")).isPresent();
    }

    @Test
    void v2InvalidKeyFailsNamingTheKey() {
        assertThatIllegalStateException()
                .isThrownBy(() -> registry(strategy(REFUNDS, "BAD KEY")))
                .withMessageContaining("'BAD KEY'")
                .withMessageContaining("refunds");
    }

    @Test
    void v2NullKeyFails() {
        assertThatIllegalStateException().isThrownBy(() -> registry(strategy(REFUNDS, null)));
    }

    @Test
    void nullGroupFromStrategyFails() {
        assertThatIllegalStateException().isThrownBy(() -> registry(strategy(null, "STANDARD")));
    }

    @Test
    void v8SameGroupNameWithDifferentTypesFailsNamingTheGroup() {
        StubStrategy<String, Res> otherTypes =
                new StubStrategy<>(REFUNDS_OTHER_TYPES, "EXPEDITED", (r, c) -> new Res("other"));

        assertThatIllegalStateException()
                .isThrownBy(() -> registry(strategy(REFUNDS, "STANDARD"), otherTypes))
                .withMessageContaining("'refunds'");
    }

    @Test
    void laterMutationOfInputListDoesNotAffectRegistry() {
        List<SwitchboardStrategy<?, ?>> input = new ArrayList<>();
        input.add(strategy(REFUNDS, "STANDARD"));
        SwitchboardRegistry registry = new SwitchboardRegistry(input, NO_CONFIG);

        input.add(strategy(REFUNDS, "EXPEDITED"));

        assertThat(registry.registered("refunds")).containsOnlyKeys("STANDARD");
        assertThat(registry.find("refunds", "EXPEDITED")).isEmpty();
    }

    @Test
    void nullConstructorArgumentsFailWithMessage() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SwitchboardRegistry(null, NO_CONFIG))
                .withMessageContaining("strategies");
        assertThatNullPointerException()
                .isThrownBy(() -> new SwitchboardRegistry(List.of(), null))
                .withMessageContaining("properties");
    }

    // --- US3: per-case kill switch (FR-014, FR-015, FR-016, FR-034) ---

    private static SwitchboardProperties disabled(String group, String key) {
        return new SwitchboardProperties(true, Map.of(group, new GroupSettings(null,
                Map.of(key, new CaseSettings(false, null, null, null)))));
    }

    @Test
    void disabledCaseIsNotFoundButStaysRegistered() {
        SwitchboardRegistry registry = new SwitchboardRegistry(
                List.of(strategy(REFUNDS, "STANDARD"), strategy(REFUNDS, "EXPEDITED")),
                disabled("refunds", "EXPEDITED"));

        assertThat(registry.find("refunds", "EXPEDITED")).isEmpty();
        assertThat(registry.registered("refunds")).containsOnlyKeys("STANDARD", "EXPEDITED");
        assertThat(registry.isEnabled("refunds", "EXPEDITED")).isFalse();
        assertThat(registry.find("refunds", "STANDARD")).isPresent();
        assertThat(registry.isEnabled("refunds", "STANDARD")).isTrue();
    }

    @Test
    void caseIsEnabledByDefault() {
        SwitchboardRegistry registry = registry(strategy(REFUNDS, "STANDARD"));

        assertThat(registry.isEnabled("refunds", "STANDARD")).isTrue();
        assertThat(registry.find("refunds", "STANDARD")).isPresent();
    }

    @Test
    void caseConfiguredWithoutEnabledFlagIsEnabled() {
        SwitchboardProperties ownerOnly = new SwitchboardProperties(true, Map.of("refunds", new GroupSettings(null,
                Map.of("STANDARD", new CaseSettings(true, "payments-team", "1.0", "Standard refund flow")))));
        SwitchboardRegistry registry = new SwitchboardRegistry(List.of(strategy(REFUNDS, "STANDARD")), ownerOnly);

        assertThat(registry.isEnabled("refunds", "STANDARD")).isTrue();
        assertThat(registry.find("refunds", "STANDARD")).isPresent();
    }

    @Test
    void disablingAKeyInOneGroupLeavesTheSameKeyInAnotherGroupEnabled() {
        SwitchboardRegistry registry = new SwitchboardRegistry(
                List.of(strategy(REFUNDS, "STANDARD"), strategy(PAYMENTS, "STANDARD")),
                disabled("refunds", "STANDARD"));

        assertThat(registry.find("refunds", "STANDARD")).isEmpty();
        assertThat(registry.isEnabled("refunds", "STANDARD")).isFalse();
        assertThat(registry.find("payments", "STANDARD")).isPresent();
        assertThat(registry.isEnabled("payments", "STANDARD")).isTrue();
    }

    @Test
    void enablementMatchesKeysCaseSensitively() {
        SwitchboardRegistry registry = new SwitchboardRegistry(
                List.of(strategy(REFUNDS, "EXPEDITED")), disabled("refunds", "expedited"));

        assertThat(registry.find("refunds", "EXPEDITED")).isPresent();
        assertThat(registry.isEnabled("refunds", "EXPEDITED")).isTrue();
    }
}
