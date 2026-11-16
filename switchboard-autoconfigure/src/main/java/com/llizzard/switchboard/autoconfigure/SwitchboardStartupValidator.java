package com.llizzard.switchboard.autoconfigure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
 *   <li>V7: a group configured under {@code switchboard.groups} has no strategies and no resolvers;</li>
 *   <li>V8: one group name is declared with different types across strategies and resolvers.</li>
 * </ul>
 *
 * <p>W1: a configured case key that matches no strategy in its group is allowed (configuration may
 * precede the implementation), but logs one WARN per key. The usual causes are a typo, or a key
 * set through an environment variable, which lower-cases it.
 *
 * <p>Public and non-final so a consuming application can replace it (FR-029).
 */
public class SwitchboardStartupValidator implements SmartInitializingSingleton {

    private static final Log LOGGER = LogFactory.getLog(SwitchboardStartupValidator.class);

    private final List<SwitchboardStrategy<?, ?>> strategies;
    private final List<SwitchboardResolver<?>> resolvers;
    private final SwitchboardProperties properties;

    /**
     * Creates a validator over the given beans and configuration.
     *
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
        validateEveryConfiguredGroupIsUsed();
        warnAboutConfiguredCasesWithoutStrategy();
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

    private void validateEveryConfiguredGroupIsUsed() {
        Set<String> used = Stream.concat(strategies.stream().map(s -> s.group().name()),
                        resolvers.stream().map(r -> r.group().name()))
                .collect(Collectors.toSet());
        new TreeSet<>(properties.groups().keySet()).stream()
                .filter(group -> !used.contains(group))
                .findFirst()
                .ifPresent(group -> {
                    throw new IllegalStateException("Switchboard group '" + group + "' is configured under"
                            + " switchboard.groups, but no strategy or resolver uses it. Check the group name for a"
                            + " typo, or remove its configuration.");
                });
    }

    private void warnAboutConfiguredCasesWithoutStrategy() {
        Map<String, Set<String>> keysByGroup = strategies.stream().collect(Collectors.groupingBy(
                s -> s.group().name(),
                Collectors.mapping(SwitchboardStrategy::caseKey, Collectors.toCollection(TreeSet::new))));
        new TreeMap<>(properties.groups()).forEach((group, settings) -> {
            Set<String> registered = keysByGroup.getOrDefault(group, Set.of());
            new TreeSet<>(settings.cases().keySet()).stream()
                    .filter(key -> !registered.contains(key))
                    .forEach(key -> LOGGER.warn("Switchboard group '" + group + "' configures case '" + key
                            + "', but no strategy in that group has this key (registered keys: " + registered
                            + "). The setting has no effect. Keys are matched exactly and case-sensitively:"
                            + " write them in bracket notation, for example switchboard.groups." + group
                            + ".cases.[<KEY>].enabled=false, and do not set them through environment variables,"
                            + " which lower-case them."));
        });
    }

    /**
     * Returns the configuration this validator checks against.
     *
     * @return the configuration this validator checks against
     */
    protected SwitchboardProperties properties() {
        return properties;
    }
}
