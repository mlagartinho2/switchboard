package com.llizzard.switchboard.autoconfigure;

import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.resolver;
import static com.llizzard.switchboard.Stubs.strategy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.llizzard.switchboard.Stubs.StubResolver;
import com.llizzard.switchboard.Stubs.StubStrategy;
import com.llizzard.switchboard.SwitchboardDispatcher;
import com.llizzard.switchboard.SwitchboardRegistry;
import com.llizzard.switchboard.SwitchboardResolverChain;
import com.llizzard.switchboard.web.SwitchboardExceptionHandler;

class SwitchboardAutoConfigurationTest {

    private static final AutoConfigurations SWITCHBOARD = AutoConfigurations.of(SwitchboardAutoConfiguration.class);

    @Test
    void servletWebContextGetsAllCoreBeans() {
        new WebApplicationContextRunner()
                .withConfiguration(SWITCHBOARD)
                .withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withBean("header", StubResolver.class, () -> resolver(REFUNDS, "h", "STANDARD"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SwitchboardRegistry.class);
                    assertThat(context).hasSingleBean(SwitchboardResolverChain.class);
                    assertThat(context).hasSingleBean(SwitchboardDispatcher.class);
                    assertThat(context).hasSingleBean(SwitchboardStartupValidator.class);
                    assertThat(context).hasSingleBean(SwitchboardProperties.class);
                    assertThat(context).hasSingleBean(SwitchboardExceptionHandler.class);
                    assertThat(context.getBean(SwitchboardRegistry.class).find("refunds", "STANDARD")).isPresent();
                    assertThat(context.getBean(SwitchboardResolverChain.class).resolutionOrder("refunds"))
                            .containsExactly("h");
                });
    }

    @Test
    void nonWebContextGetsNoSwitchboardBeans() {
        new ApplicationContextRunner()
                .withConfiguration(SWITCHBOARD)
                .withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withBean("header", StubResolver.class, () -> resolver(REFUNDS, "h", "STANDARD"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SwitchboardRegistry.class);
                    assertThat(context).doesNotHaveBean(SwitchboardResolverChain.class);
                    assertThat(context).doesNotHaveBean(SwitchboardDispatcher.class);
                    assertThat(context).doesNotHaveBean(SwitchboardStartupValidator.class);
                    assertThat(context).doesNotHaveBean(SwitchboardProperties.class);
                    assertThat(context).doesNotHaveBean(SwitchboardExceptionHandler.class);
                });
    }

    @Test
    void disabledLibraryRegistersNoSwitchboardBeans() {
        new WebApplicationContextRunner()
                .withConfiguration(SWITCHBOARD)
                .withPropertyValues("switchboard.enabled=false")
                .withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withBean("header", StubResolver.class, () -> resolver(REFUNDS, "h", "STANDARD"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SwitchboardRegistry.class);
                    assertThat(context).doesNotHaveBean(SwitchboardResolverChain.class);
                    assertThat(context).doesNotHaveBean(SwitchboardDispatcher.class);
                    assertThat(context).doesNotHaveBean(SwitchboardStartupValidator.class);
                    assertThat(context).doesNotHaveBean(SwitchboardExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(SwitchboardProperties.class);
                    // Consumer strategies and resolvers are plain beans and still load harmlessly (§R11).
                    assertThat(context).hasBean("standard").hasBean("header");
                });
    }

    @Test
    void disabledLibraryIgnoresOtherwiseInvalidConfiguration() {
        new WebApplicationContextRunner()
                .withConfiguration(SWITCHBOARD)
                .withPropertyValues("switchboard.enabled=false", "switchboard.groups.refunds.resolution-order=zzz")
                .withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void resolversAreOrderedByBeanOrder() {
        new WebApplicationContextRunner()
                .withConfiguration(SWITCHBOARD)
                .withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withUserConfiguration(OrderedResolvers.class)
                .run(context -> assertThat(context.getBean(SwitchboardResolverChain.class).resolutionOrder("refunds"))
                        .containsExactly("first", "second"));
    }

    // --- US5: every library bean can be replaced by a consumer bean (FR-029, SC-007) ---

    private static final SwitchboardProperties NO_CONFIG = new SwitchboardProperties(true, null);

    private static <T> void assertReplaces(Class<T> type, T userBean) {
        new WebApplicationContextRunner()
                .withConfiguration(SWITCHBOARD)
                .withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
                .withBean("header", StubResolver.class, () -> resolver(REFUNDS, "h", "STANDARD"))
                .withBean("userBean", type, () -> userBean)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(type)).isSameAs(userBean);
                    assertThat(context.getBeansOfType(type)).hasSize(1);
                });
    }

    @Test
    void userRegistryReplacesTheLibraryOne() {
        assertReplaces(SwitchboardRegistry.class, new SwitchboardRegistry(List.of(), NO_CONFIG) {
        });
    }

    @Test
    void userResolverChainReplacesTheLibraryOne() {
        assertReplaces(SwitchboardResolverChain.class, new SwitchboardResolverChain(List.of(), NO_CONFIG) {
        });
    }

    @Test
    void userDispatcherReplacesTheLibraryOne() {
        SwitchboardDispatcher userDispatcher = new SwitchboardDispatcher(
                new SwitchboardResolverChain(List.of(), NO_CONFIG), new SwitchboardRegistry(List.of(), NO_CONFIG)) {
        };
        assertReplaces(SwitchboardDispatcher.class, userDispatcher);
    }

    @Test
    void userStartupValidatorReplacesTheLibraryOne() {
        assertReplaces(SwitchboardStartupValidator.class,
                new SwitchboardStartupValidator(List.of(), List.of(), NO_CONFIG) {
                });
    }

    @Test
    void userExceptionHandlerReplacesTheLibraryOne() {
        assertReplaces(SwitchboardExceptionHandler.class, new SwitchboardExceptionHandler() {
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class OrderedResolvers {

        @Bean
        @Order(2)
        StubResolver<?> second() {
            return resolver(REFUNDS, "second", null);
        }

        @Bean
        @Order(1)
        StubResolver<?> first() {
            return resolver(REFUNDS, "first", null);
        }
    }
}
