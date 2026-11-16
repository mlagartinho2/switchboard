package com.llizzard.switchboard.autoconfigure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.SmartInitializingSingleton;

import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardResolver;
import com.llizzard.switchboard.SwitchboardStrategy;

/**
 * Checks the rules that span strategies, resolvers, and configuration, once every singleton
 * exists and before the application takes traffic. Violations throw
 * {@link IllegalStateException}, which aborts startup:
 * <ul>
 *   <li>V6: a group has strategies but no resolvers;</li>
 *   <li>V8: one group name is declared with different types across strategies and resolvers.</li>
 * </ul>
 *
 * <p>Public and non-final so a consuming application can replace it (FR-029).
 */
public class SwitchboardStartupValidator implements SmartInitializingSingleton {

    private final List<SwitchboardStrategy<?, ?>> strategies;
    private final List<SwitchboardResolver<?>> resolvers;
    private final SwitchboardProperties properties;

    /**
     * @param strategies every strategy bean; copied
     * @param resolvers every resolver bean; copied
     * @param properties the bound configuration
     * @throws NullPointerException if an argument is null
     */
    public SwitchboardStartupValidator(List<SwitchboardStrategy<?, ?>> strategies,
            List<SwitchboardResolver<?>> resolvers, SwitchboardProperties properties) {
        this.strategies = List.copyOf(Objects.requireNonNull(strategies, "strategies must not be null"));
        this.resolvers = List.copyOf(Objects.requireNonNull(resolvers, "resolvers must not be null"));
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public void afterSingletonsInstantiated() {
        validateGroupTypes();
        validateEveryStrategyGroupHasResolvers();
    }

    private void validateGroupTypes() {
        Map<String, SwitchboardGroup<?, ?>> byName = new HashMap<>();
        Stream.concat(strategies.stream().map(SwitchboardStrategy::group),
                        resolvers.stream().map(SwitchboardResolver::group))
                .forEach(handle -> {
                    SwitchboardGroup<?, ?> existing = byName.putIfAbsent(handle.name(), handle);
                    if (existing != null && !existing.equals(handle)) {
                        throw new IllegalStateException("Switchboard group '" + handle.name()
                                + "' is declared with different types across strategies and resolvers: "
                                + describe(existing) + " and " + describe(handle));
                    }
                });
    }

    private static String describe(SwitchboardGroup<?, ?> handle) {
        return "<" + handle.requestType().getName() + ", " + handle.responseType().getName() + ">";
    }

    private void validateEveryStrategyGroupHasResolvers() {
        Set<String> withResolvers = resolvers.stream().map(r -> r.group().name()).collect(Collectors.toSet());
        TreeSet<String> withoutResolvers = strategies.stream()
                .map(s -> s.group().name())
                .filter(name -> !withResolvers.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!withoutResolvers.isEmpty()) {
            String group = withoutResolvers.first();
            throw new IllegalStateException("Switchboard group '" + group
                    + "' has strategies but no resolvers: no request could ever be routed to it."
                    + " Register at least one SwitchboardResolver for group '" + group + "'.");
        }
    }

    /** @return the configuration this validator checks against */
    protected SwitchboardProperties properties() {
        return properties;
    }
}
