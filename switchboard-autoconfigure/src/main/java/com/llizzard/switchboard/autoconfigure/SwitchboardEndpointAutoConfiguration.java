package com.llizzard.switchboard.autoconfigure;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.llizzard.switchboard.SwitchboardRegistry;
import com.llizzard.switchboard.SwitchboardResolverChain;
import com.llizzard.switchboard.actuate.SwitchboardEndpoint;

/**
 * Registers the {@code switchboard} actuator endpoint. Backs off when actuator is not on the
 * classpath (FR-026), when Switchboard itself is off, when the endpoint is unavailable
 * ({@code management.endpoint.switchboard.access=none}, or not exposed), and when the application
 * defines its own {@link SwitchboardEndpoint} (FR-029).
 */
@AutoConfiguration(after = SwitchboardAutoConfiguration.class)
@ConditionalOnClass(Endpoint.class)
@ConditionalOnBean(SwitchboardRegistry.class)
public class SwitchboardEndpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint
    public SwitchboardEndpoint switchboardEndpoint(SwitchboardRegistry registry, SwitchboardResolverChain chain,
            SwitchboardProperties properties) {
        return new SwitchboardEndpoint(registry, chain, properties);
    }
}
