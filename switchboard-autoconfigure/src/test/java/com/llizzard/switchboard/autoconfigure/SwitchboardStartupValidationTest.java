package com.llizzard.switchboard.autoconfigure;

import static com.llizzard.switchboard.Stubs.PAYMENTS;
import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.REFUNDS_OTHER_TYPES;
import static com.llizzard.switchboard.Stubs.resolver;
import static com.llizzard.switchboard.Stubs.strategy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.NestedExceptionUtils;

import com.llizzard.switchboard.Stubs.Req;
import com.llizzard.switchboard.Stubs.Res;
import com.llizzard.switchboard.Stubs.StubResolver;
import com.llizzard.switchboard.Stubs.StubStrategy;
import com.llizzard.switchboard.SwitchboardDispatcher;
import com.llizzard.switchboard.SwitchboardGroup;

class SwitchboardStartupValidationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SwitchboardAutoConfiguration.class));

    @Test
    void v6GroupWithStrategyButNoResolverFailsStartupNamingTheGroup() {
        runner.withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withBean("card", StubStrategy.class, () -> strategy(PAYMENTS, "CARD"))
                .withBean("paymentResolver", StubResolver.class, () -> resolver(PAYMENTS, "p", null))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("'refunds'");
                });
    }

    @Test
    void v8StrategyAndResolverWithDifferentTypesFailStartup() {
        runner.withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withBean("otherTypes", StubResolver.class,
                        () -> new StubResolver<>(REFUNDS_OTHER_TYPES, "h", (r, p) -> Optional.empty()))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("'refunds'");
                });
    }

    @Test
    void validSetupStarts() {
        runner.withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withBean("header", StubResolver.class, () -> resolver(REFUNDS, "h", "STANDARD"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SwitchboardDispatcher.class);
                });
    }

    @Test
    void groupWithOnlyAResolverStarts() {
        SwitchboardGroup<Req, Res> orphans = SwitchboardGroup.of("orphans", Req.class, Res.class);
        runner.withBean("orphanResolver", StubResolver.class,
                        () -> new StubResolver<>(orphans, "o", (r, p) -> Optional.empty()))
                .run(context -> assertThat(context).hasNotFailed());
    }

    // --- US3: configuration checks through the full context (V4, V7, W1) ---

    @Test
    void v7ConfiguredGroupWithNothingInItFailsStartup() {
        runner.withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withBean("header", StubResolver.class, () -> resolver(REFUNDS, "h", "STANDARD"))
                .withPropertyValues("switchboard.groups.ghost.cases.[X].enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("'ghost'");
                });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void w1ConfiguredCaseWithoutStrategyStartsAndWarns(CapturedOutput output) {
        runner.withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "REFUND_STANDARD"))
                .withBean("header", StubResolver.class, () -> resolver(REFUNDS, "h", "REFUND_STANDARD"))
                .withPropertyValues("switchboard.groups.refunds.cases.[REFUNDSTANDARD].owner=x")
                .run(context -> assertThat(context).hasNotFailed());

        assertThat(output.getAll().lines().filter(line -> line.contains("WARN")))
                .anySatisfy(line -> assertThat(line).contains("'refunds'").contains("'REFUNDSTANDARD'"));
    }

    @Test
    void v4UnknownResolverInResolutionOrderFailsStartup() {
        runner.withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withBean("header", StubResolver.class, () -> resolver(REFUNDS, "h", "STANDARD"))
                .withPropertyValues("switchboard.groups.refunds.resolution-order=h,zzz")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("'refunds'")
                            .hasMessageContaining("'zzz'");
                });
    }

    /** The innermost cause, or the failure itself when Spring did not wrap it. */
    private static Throwable rootCause(Throwable failure) {
        return NestedExceptionUtils.getMostSpecificCause(failure);
    }
}
