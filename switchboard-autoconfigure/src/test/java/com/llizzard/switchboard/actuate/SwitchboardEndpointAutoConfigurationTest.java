package com.llizzard.switchboard.actuate;

import static com.llizzard.switchboard.Stubs.REFUNDS;
import static com.llizzard.switchboard.Stubs.resolver;
import static com.llizzard.switchboard.Stubs.strategy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.convert.support.ConfigurableConversionService;

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

    // SpringApplication installs this conversion service on the Environment; it lets the documented
    // lower-case "none" convert to Access.NONE, as in a real application.
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment()
                    .setConversionService(
                            (ConfigurableConversionService) ApplicationConversionService.getSharedInstance()))
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
    void endpointIsAbsentUntilExposed() {
        // Boot's availability rule for every endpoint: default access is not enough, it must be exposed.
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SwitchboardEndpoint.class);
        });
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

    // --- US5: the endpoint can be replaced by a consumer bean (FR-029, SC-007) ---

    @Test
    void userEndpointReplacesTheLibraryOne() {
        SwitchboardProperties noConfig = new SwitchboardProperties(true, null);
        SwitchboardEndpoint userEndpoint = new SwitchboardEndpoint(new SwitchboardRegistry(List.of(), noConfig),
                new SwitchboardResolverChain(List.of(), noConfig), noConfig) {
        };

        runner.withPropertyValues(EXPOSE)
                .withBean("userEndpoint", SwitchboardEndpoint.class, () -> userEndpoint)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SwitchboardEndpoint.class)).isSameAs(userEndpoint);
                    assertThat(context.getBeansOfType(SwitchboardEndpoint.class)).hasSize(1);
                });
    }
}
