package com.llizzard.switchboard.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.llizzard.switchboard.SwitchboardDispatcher;
import com.llizzard.switchboard.SwitchboardRegistry;
import com.llizzard.switchboard.SwitchboardResolver;
import com.llizzard.switchboard.SwitchboardResolverChain;
import com.llizzard.switchboard.SwitchboardStrategy;
import com.llizzard.switchboard.web.SwitchboardExceptionHandler;

/**
 * Wires Switchboard into a servlet web application. Every bean backs off when the application
 * defines its own bean of the same type (FR-029), and the whole configuration backs off with
 * {@code switchboard.enabled=false} (FR-017).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnProperty(prefix = "switchboard", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SwitchboardProperties.class)
public class SwitchboardAutoConfiguration {

    /** Creates the auto-configuration; instantiated by Spring Boot. */
    public SwitchboardAutoConfiguration() {
    }

    /**
     * The strategy registry, built from every {@link SwitchboardStrategy} bean.
     *
     * @param strategies every strategy bean
     * @param properties the bound configuration
     * @return the registry
     */
    @Bean
    @ConditionalOnMissingBean
    public SwitchboardRegistry switchboardRegistry(ObjectProvider<SwitchboardStrategy<?, ?>> strategies,
            SwitchboardProperties properties) {
        return new SwitchboardRegistry(strategies.orderedStream().toList(), properties);
    }

    /**
     * The resolver chain, built from every {@link SwitchboardResolver} bean in bean order.
     *
     * @param resolvers every resolver bean
     * @param properties the bound configuration
     * @return the chain
     */
    @Bean
    @ConditionalOnMissingBean
    public SwitchboardResolverChain switchboardResolverChain(ObjectProvider<SwitchboardResolver<?>> resolvers,
            SwitchboardProperties properties) {
        // Spring bean order (@Order / Ordered, then registration order) is the default resolution order (§R6).
        return new SwitchboardResolverChain(resolvers.orderedStream().toList(), properties);
    }

    /**
     * The dispatcher that controllers call.
     *
     * @param chain the resolver chain
     * @param registry the strategy registry
     * @return the dispatcher
     */
    @Bean
    @ConditionalOnMissingBean
    public SwitchboardDispatcher switchboardDispatcher(SwitchboardResolverChain chain, SwitchboardRegistry registry) {
        return new SwitchboardDispatcher(chain, registry);
    }

    /**
     * The startup validator (rules V6, V7, V8 and warning W1).
     *
     * @param strategies every strategy bean
     * @param resolvers every resolver bean
     * @param properties the bound configuration
     * @return the validator
     */
    @Bean
    @ConditionalOnMissingBean
    public SwitchboardStartupValidator switchboardStartupValidator(
            ObjectProvider<SwitchboardStrategy<?, ?>> strategies, ObjectProvider<SwitchboardResolver<?>> resolvers,
            SwitchboardProperties properties) {
        return new SwitchboardStartupValidator(
                strategies.orderedStream().toList(), resolvers.orderedStream().toList(), properties);
    }

    /**
     * The handler that turns dispatch failures into 501 and 404 problem responses.
     *
     * @return the exception handler
     */
    @Bean
    @ConditionalOnMissingBean
    public SwitchboardExceptionHandler switchboardExceptionHandler() {
        return new SwitchboardExceptionHandler();
    }
}
