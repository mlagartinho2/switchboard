package com.llizzard.switchboard.autoconfigure;

import static com.llizzard.switchboard.Stubs.PAYMENTS;
import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.REFUNDS_OTHER_TYPES;
import static com.llizzard.switchboard.Stubs.resolver;
import static com.llizzard.switchboard.Stubs.strategy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.llizzard.switchboard.Stubs.Res;
import com.llizzard.switchboard.Stubs.StubResolver;
import com.llizzard.switchboard.Stubs.StubStrategy;
import com.llizzard.switchboard.SwitchboardResolver;
import com.llizzard.switchboard.SwitchboardStrategy;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.CaseSettings;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.GroupSettings;

/** Plain JUnit, no Spring context (constitution, Testing section). */
class SwitchboardStartupValidatorTest {

    private static final SwitchboardProperties NO_CONFIG = new SwitchboardProperties(true, null);

    private static SwitchboardStartupValidator validator(List<SwitchboardStrategy<?, ?>> strategies,
            List<SwitchboardResolver<?>> resolvers) {
        return new SwitchboardStartupValidator(strategies, resolvers, NO_CONFIG);
    }

    @Test
    void v6GroupWithStrategyButNoResolverFailsNamingTheGroup() {
        SwitchboardStartupValidator validator = validator(
                List.of(strategy(REFUNDS, "STANDARD"), strategy(PAYMENTS, "CARD")),
                List.of(resolver(PAYMENTS, "p", null)));

        assertThatIllegalStateException()
                .isThrownBy(validator::afterSingletonsInstantiated)
                .withMessageContaining("'refunds'")
                .withMessageContaining("no resolvers");
    }

    @Test
    void v8StrategyAndResolverWithSameGroupNameButDifferentTypesFail() {
        StubResolver<String> otherTypes = new StubResolver<>(REFUNDS_OTHER_TYPES, "h", (r, p) -> Optional.empty());
        SwitchboardStartupValidator validator = validator(List.of(strategy(REFUNDS, "STANDARD")), List.of(otherTypes));

        assertThatIllegalStateException()
                .isThrownBy(validator::afterSingletonsInstantiated)
                .withMessageContaining("'refunds'");
    }

    @Test
    void v8AmongStrategiesAloneFails() {
        StubStrategy<String, Res> otherTypes =
                new StubStrategy<>(REFUNDS_OTHER_TYPES, "EXPEDITED", (r, c) -> new Res("x"));
        SwitchboardStartupValidator validator = validator(
                List.of(strategy(REFUNDS, "STANDARD"), otherTypes), List.of(resolver(REFUNDS, "h", null)));

        assertThatIllegalStateException()
                .isThrownBy(validator::afterSingletonsInstantiated)
                .withMessageContaining("'refunds'");
    }

    @Test
    void validSetupDoesNotThrow() {
        SwitchboardStartupValidator validator = validator(
                List.of(strategy(REFUNDS, "STANDARD"), strategy(REFUNDS, "EXPEDITED"), strategy(PAYMENTS, "CARD")),
                List.of(resolver(REFUNDS, "h", null), resolver(REFUNDS, "a", null), resolver(PAYMENTS, "p", null)));

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void groupWithResolverButNoStrategiesDoesNotThrow() {
        SwitchboardStartupValidator validator = validator(List.of(), List.of(resolver(REFUNDS, "h", null)));

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void emptySetupDoesNotThrow() {
        assertThatCode(validator(List.of(), List.of())::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void nullConstructorArgumentsFailWithMessage() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SwitchboardStartupValidator(null, List.of(), NO_CONFIG))
                .withMessageContaining("strategies");
        assertThatNullPointerException()
                .isThrownBy(() -> new SwitchboardStartupValidator(List.of(), null, NO_CONFIG))
                .withMessageContaining("resolvers");
        assertThatNullPointerException()
                .isThrownBy(() -> new SwitchboardStartupValidator(List.of(), List.of(), null))
                .withMessageContaining("properties");
    }

    @Test
    void laterMutationOfInputListsDoesNotChangeTheOutcome() {
        List<SwitchboardStrategy<?, ?>> strategies = new ArrayList<>();
        List<SwitchboardResolver<?>> resolvers = new ArrayList<>();
        strategies.add(strategy(REFUNDS, "STANDARD"));
        resolvers.add(resolver(REFUNDS, "h", null));
        SwitchboardStartupValidator valid = new SwitchboardStartupValidator(strategies, resolvers, NO_CONFIG);

        strategies.add(strategy(PAYMENTS, "CARD")); // would be V6 if seen
        assertThatCode(valid::afterSingletonsInstantiated).doesNotThrowAnyException();

        List<SwitchboardStrategy<?, ?>> orphanStrategies = new ArrayList<>(List.of(strategy(PAYMENTS, "CARD")));
        List<SwitchboardResolver<?>> lateResolvers = new ArrayList<>();
        SwitchboardStartupValidator invalid =
                new SwitchboardStartupValidator(orphanStrategies, lateResolvers, NO_CONFIG);

        lateResolvers.add(resolver(PAYMENTS, "p", null)); // would fix V6 if seen
        assertThatIllegalStateException().isThrownBy(invalid::afterSingletonsInstantiated);
    }

    // --- US3: configuration checks (V7, W1) ---

    private static SwitchboardProperties cases(String group, String... keys) {
        Map<String, CaseSettings> cases = new LinkedHashMap<>();
        for (String key : keys) {
            cases.put(key, new CaseSettings(true, "x", null, null));
        }
        return new SwitchboardProperties(true, Map.of(group, new GroupSettings(null, cases)));
    }

    @Test
    void v7ConfiguredGroupWithNoStrategiesOrResolversFailsNamingTheGroup() {
        SwitchboardStartupValidator validator = new SwitchboardStartupValidator(
                List.of(strategy(REFUNDS, "STANDARD")), List.of(resolver(REFUNDS, "h", null)), cases("ghost", "X"));

        assertThatIllegalStateException()
                .isThrownBy(validator::afterSingletonsInstantiated)
                .withMessageContaining("'ghost'");
    }

    @Test
    void v7ConfiguredGroupUsedOnlyByAResolverIsAccepted() {
        SwitchboardStartupValidator validator = new SwitchboardStartupValidator(
                List.of(), List.of(resolver(REFUNDS, "h", null)), new SwitchboardProperties(true,
                        Map.of("refunds", new GroupSettings(List.of("h"), null))));

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void w1ConfiguredCaseWithoutStrategyOnlyWarns(CapturedOutput output) {
        SwitchboardStartupValidator validator = new SwitchboardStartupValidator(
                List.of(strategy(REFUNDS, "REFUND_STANDARD")), List.of(resolver(REFUNDS, "h", null)),
                cases("refunds", "REFUNDSTANDARD"));

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();

        List<String> warnings = output.getAll().lines().filter(line -> line.contains("WARN")).toList();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("'refunds'")
                .contains("'REFUNDSTANDARD'")
                .contains("REFUND_STANDARD")
                .containsIgnoringCase("bracket");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void w1ConfiguredCaseWithMatchingStrategyDoesNotWarn(CapturedOutput output) {
        SwitchboardStartupValidator validator = new SwitchboardStartupValidator(
                List.of(strategy(REFUNDS, "STANDARD")), List.of(resolver(REFUNDS, "h", null)),
                cases("refunds", "STANDARD"));

        validator.afterSingletonsInstantiated();

        assertThat(output.getAll()).doesNotContain("WARN");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void w1WarnsOncePerUnmatchedKey(CapturedOutput output) {
        SwitchboardStartupValidator validator = new SwitchboardStartupValidator(
                List.of(strategy(REFUNDS, "STANDARD")), List.of(resolver(REFUNDS, "h", null)),
                cases("refunds", "STANDARD", "GONE", "ALSOGONE"));

        validator.afterSingletonsInstantiated();

        List<String> warnings = output.getAll().lines().filter(line -> line.contains("WARN")).toList();
        assertThat(warnings).hasSize(2);
        assertThat(warnings).anySatisfy(line -> assertThat(line).contains("'GONE'"));
        assertThat(warnings).anySatisfy(line -> assertThat(line).contains("'ALSOGONE'"));
    }
}
