package com.llizzard.switchboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import com.llizzard.switchboard.autoconfigure.SwitchboardProperties;

/**
 * Consults a group's {@link SwitchboardResolver resolvers} in order and returns the first key
 * one of them produces. Built once at startup and immutable afterwards.
 *
 * <p>Without a configured {@code resolution-order}, a group's resolvers are consulted in the
 * order they were given, which is Spring bean order ({@code @Order}) when auto-configured. With
 * {@code switchboard.groups.<group>.resolution-order}, exactly the named resolvers are consulted,
 * in the configured order; resolvers left out are never consulted.
 *
 * <p>The constructor validates, and fails with {@link IllegalStateException}, when:
 * <ul>
 *   <li>two resolvers in one group share a name, or a name is blank (V3);</li>
 *   <li>a configured {@code resolution-order} names a resolver that is not in its group (V4);</li>
 *   <li>a configured {@code resolution-order} is present but empty (V5);</li>
 *   <li>resolvers use the same group name with different request/response types (V8).</li>
 * </ul>
 *
 * <p>Public and non-final so a consuming application can replace it (FR-029).
 */
public class SwitchboardResolverChain {

    private final Map<String, List<SwitchboardResolver<?>>> resolversByGroup;
    private final Map<String, SwitchboardGroup<?, ?>> handlesByName;
    private final SwitchboardProperties properties;

    /**
     * @param orderedResolvers every resolver bean, in consultation order; copied
     * @param properties the bound configuration
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if a rule V3, V4, V5, or V8 is violated
     */
    public SwitchboardResolverChain(List<SwitchboardResolver<?>> orderedResolvers, SwitchboardProperties properties) {
        Objects.requireNonNull(orderedResolvers, "orderedResolvers must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        List<SwitchboardResolver<?>> copy = List.copyOf(orderedResolvers);
        copy.forEach(SwitchboardResolverChain::requireGroup);
        this.handlesByName = GroupHandles.requireConsistent(
                copy.stream().<SwitchboardGroup<?, ?>>map(SwitchboardResolver::group).toList(), "resolvers");
        Map<String, List<SwitchboardResolver<?>>> byGroup = copy.stream()
                .collect(Collectors.groupingBy(r -> r.group().name(), LinkedHashMap::new, Collectors.toList()));
        Map<String, List<SwitchboardResolver<?>>> index = new HashMap<>();
        byGroup.forEach((group, members) -> {
            validateNames(group, members);
            index.put(group, ordered(group, members, properties.group(group).resolutionOrder()));
        });
        // An order configured for a group with no resolvers at all can only name unknown resolvers (V4/V5).
        new TreeMap<>(properties.groups()).forEach((group, settings) -> {
            if (!byGroup.containsKey(group)) {
                ordered(group, List.of(), settings.resolutionOrder());
            }
        });
        this.resolversByGroup = Map.copyOf(index);
    }

    private static void requireGroup(SwitchboardResolver<?> resolver) {
        if (resolver.group() == null) {
            throw new IllegalStateException(
                    "Switchboard resolver " + resolver.getClass().getName() + " returned a null group()");
        }
    }

    /**
     * @param members the group's resolvers, in the given order; names already validated (V3)
     * @param configuredOrder the configured {@code resolution-order}, or null when absent
     * @return the resolvers to consult, in order
     */
    private static List<SwitchboardResolver<?>> ordered(String group, List<SwitchboardResolver<?>> members,
            List<String> configuredOrder) {
        if (configuredOrder == null) {
            return List.copyOf(members);
        }
        if (configuredOrder.isEmpty()) {
            throw new IllegalStateException("Switchboard group '" + group + "' has an empty resolution-order:"
                    + " name at least one resolver, or remove the setting to consult every resolver in bean order.");
        }
        Map<String, SwitchboardResolver<?>> byName = members.stream()
                .collect(Collectors.toMap(SwitchboardResolver::name, r -> r));
        List<SwitchboardResolver<?>> ordered = new ArrayList<>(configuredOrder.size());
        for (String name : configuredOrder) {
            SwitchboardResolver<?> resolver = byName.get(name);
            if (resolver == null) {
                throw new IllegalStateException("Switchboard group '" + group + "' resolution-order names unknown"
                        + " resolver '" + name + "'; resolvers in this group: "
                        + members.stream().map(SwitchboardResolver::name).toList());
            }
            ordered.add(resolver);
        }
        return List.copyOf(ordered);
    }

    private static void validateNames(String group, List<SwitchboardResolver<?>> resolvers) {
        Map<String, SwitchboardResolver<?>> seen = new HashMap<>();
        for (SwitchboardResolver<?> resolver : resolvers) {
            String name = resolver.name();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("Switchboard resolver " + resolver.getClass().getName()
                        + " in group '" + group + "' has a blank name()");
            }
            SwitchboardResolver<?> previous = seen.putIfAbsent(name, resolver);
            if (previous != null) {
                throw new IllegalStateException("Duplicate resolver name '" + name + "' in group '" + group + "': "
                        + previous.getClass().getName() + " and " + resolver.getClass().getName());
            }
        }
    }

    /**
     * Consults the group's resolvers in order. Empty or blank results mean "no key"; the first
     * non-blank key wins and later resolvers are not consulted. Resolver exceptions propagate
     * unchanged.
     *
     * @param group the group handle
     * @param httpRequest the current HTTP request
     * @param payload the request payload; may be null
     * @return the winning key and resolver name, or empty when no resolver produced a key
     * @throws IllegalArgumentException if {@code group} has the same name as the registered group
     *         but different types
     */
    public <Req> Optional<SwitchboardResolution> resolve(SwitchboardGroup<Req, ?> group,
            HttpServletRequest httpRequest, Req payload) {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(httpRequest, "httpRequest must not be null");
        SwitchboardGroup<?, ?> registered = handlesByName.get(group.name());
        if (registered != null && !registered.equals(group)) {
            throw new IllegalArgumentException("Switchboard group '" + group.name() + "' was dispatched with types "
                    + GroupHandles.describe(group) + " but its resolvers use " + GroupHandles.describe(registered));
        }
        for (SwitchboardResolver<?> resolver : resolversByGroup.getOrDefault(group.name(), List.of())) {
            // Safe: resolvers are grouped by group name, and startup validation (V8) guarantees every
            // resolver under this group name was compiled against SwitchboardGroup<Req, ?>.
            @SuppressWarnings("unchecked")
            SwitchboardResolver<Req> typed = (SwitchboardResolver<Req>) resolver;
            Optional<String> key = typed.resolve(httpRequest, payload);
            if (key != null && key.isPresent() && !key.get().isBlank()) {
                return Optional.of(new SwitchboardResolution(key.get(), typed.name()));
            }
        }
        return Optional.empty();
    }

    /**
     * @param group the group name
     * @return the names of the resolvers consulted for the group, in order; empty for an unknown group
     */
    public List<String> resolutionOrder(String group) {
        return resolversByGroup.getOrDefault(group, List.of()).stream()
                .map(SwitchboardResolver::name)
                .toList();
    }

    /** @return the names of the groups that have at least one resolver */
    public Set<String> groups() {
        return resolversByGroup.keySet();
    }

    /** @return the configuration this chain was built with */
    protected SwitchboardProperties properties() {
        return properties;
    }
}
