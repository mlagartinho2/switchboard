package com.llizzard.switchboard.actuate;

import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.resolver;
import static com.llizzard.switchboard.Stubs.strategy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.llizzard.switchboard.Stubs.StubResolver;
import com.llizzard.switchboard.Stubs.StubStrategy;
import com.llizzard.switchboard.SwitchboardDispatcher;
import com.llizzard.switchboard.SwitchboardRegistry;
import com.llizzard.switchboard.SwitchboardResolverChain;
import com.llizzard.switchboard.autoconfigure.SwitchboardAutoConfiguration;
import com.llizzard.switchboard.autoconfigure.SwitchboardEndpointAutoConfiguration;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties;

class SwitchboardEndpointAutoConfigurationTest {

    private static final String EXPOSE = "management.endpoints.web.exposure.include=switchboard";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SwitchboardAutoConfiguration.class,
                    SwitchboardEndpointAutoConfiguration.class, EndpointAutoConfiguration.class))
            .withBean("standard", StubStrategy.class, () -> strategy(REFUNDS, "STANDARD"))
            .withBean("header", StubResolver.class, () -> resolver(REFUNDS, "h", "STANDARD"));

    @Test
    void endpointIsPresentWhenExposedWithDefaultAccess() {
        runner.withPropertyValues(EXPOSE)
                .run(context -> assertThat(context).hasSingleBean(SwitchboardEndpoint.class));
    }

    @Test
    void endpointIsAbsentWhenAccessIsNone() {
        runner.withPropertyValues(EXPOSE, "management.endpoint.switchboard.access=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SwitchboardEndpoint.class);
                });
    }

    @Test
    void endpointIsAbsentAndAppStartsWithoutActuator() {
        runner.withPropertyValues(EXPOSE)
                .withClassLoader(new FilteredClassLoader(Endpoint.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SwitchboardEndpoint.class);
                    assertThat(context).hasSingleBean(SwitchboardDispatcher.class);
                });
    }

    @Test
    void endpointIsAbsentWhenSwitchboardIsDisabled() {
        runner.withPropertyValues(EXPOSE, "switchboard.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SwitchboardEndpoint.class);
                });
    }

    @Test
    void consumerEndpointBeanReplacesTheLibraryOne() {
        runner.withPropertyValues(EXPOSE)
                .withUserConfiguration(CustomEndpointConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SwitchboardEndpoint.class);
                    assertThat(context.getBean(SwitchboardEndpoint.class)).isInstanceOf(CustomEndpoint.class);
                });
    }

    static class CustomEndpoint extends SwitchboardEndpoint {
        CustomEndpoint(SwitchboardRegistry registry, SwitchboardResolverChain chain, SwitchboardProperties properties) {
            super(registry, chain, properties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomEndpointConfiguration {

        @Bean
        CustomEndpoint customEndpoint(SwitchboardRegistry registry, SwitchboardResolverChain chain,
                SwitchboardProperties properties) {
            return new CustomEndpoint(registry, chain, properties);
        }
    }
}
