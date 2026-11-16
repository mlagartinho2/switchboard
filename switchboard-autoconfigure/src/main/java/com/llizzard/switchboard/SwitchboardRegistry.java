package com.llizzard.switchboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.llizzard.switchboard.autoconfigure.SwitchboardProperties;

/**
 * Holds every registered {@link SwitchboardStrategy}, indexed by group name and use-case key.
 * Built once at startup and immutable afterwards.
 *
 * <p>The constructor validates, and fails with {@link IllegalStateException}, when:
 * <ul>
 *   <li>two strategies in one group share a key (V1; the message names both classes);</li>
 *   <li>a key does not match {@code [A-Za-z0-9_-]+} (V2);</li>
 *   <li>strategies use the same group name with different request/response types (V8).</li>
 * </ul>
 *
 * <p>Public and non-final so a consuming application can replace it (FR-029).
 */
public class SwitchboardRegistry {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final Map<String, Map<String, SwitchboardStrategy<?, ?>>> strategiesByGroup;
    private final SwitchboardProperties properties;

    /**
     * @param strategies every strategy bean; copied
     * @param properties the bound configuration
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if a rule V1, V2, or V8 is violated
     */
    public SwitchboardRegistry(List<SwitchboardStrategy<?, ?>> strategies, SwitchboardProperties properties) {
        Objects.requireNonNull(strategies, "strategies must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        List<SwitchboardStrategy<?, ?>> copy = List.copyOf(strategies);
        copy.forEach(SwitchboardRegistry::validateKey);
        GroupHandles.requireConsistent(copy.stream().map(SwitchboardStrategy::group).toList(), "strategies");
        this.strategiesByGroup = index(copy);
    }

    private static void validateKey(SwitchboardStrategy<?, ?> strategy) {
        SwitchboardGroup<?, ?> group = strategy.group();
        if (group == null) {
            throw new IllegalStateException(
                    "Switchboard strategy " + strategy.getClass().getName() + " returned a null group()");
        }
        String key = strategy.caseKey();
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalStateException("Invalid use-case key '" + key + "' in group '" + group.name()
                    + "' from " + strategy.getClass().getName() + ": keys must match " + KEY_PATTERN.pattern());
        }
    }

    private static Map<String, Map<String, SwitchboardStrategy<?, ?>>> index(List<SwitchboardStrategy<?, ?>> strategies) {
        Map<String, List<SwitchboardStrategy<?, ?>>> byGroup = strategies.stream()
                .collect(Collectors.groupingBy(s -> s.group().name()));
        Map<String, Map<String, SwitchboardStrategy<?, ?>>> index = new HashMap<>();
        byGroup.forEach((group, members) -> index.put(group, Map.copyOf(members.stream()
                .collect(Collectors.toMap(SwitchboardStrategy::caseKey, s -> s, (a, b) -> {
                    throw new IllegalStateException("Duplicate use-case key '" + a.caseKey() + "' in group '"
                            + group + "': " + a.getClass().getName() + " and " + b.getClass().getName());
                })))));
        return Map.copyOf(index);
    }

    /**
     * @param group the group name
     * @param caseKey the use-case key, matched exactly (case-sensitive, not trimmed)
     * @return the strategy registered for that key in that group, or empty
     */
    public Optional<SwitchboardStrategy<?, ?>> find(String group, String caseKey) {
        return Optional.ofNullable(registered(group).get(caseKey));
    }

    /**
     * @param group the group name
     * @return every strategy registered in the group, by key; empty for an unknown group
     */
    public Map<String, SwitchboardStrategy<?, ?>> registered(String group) {
        return strategiesByGroup.getOrDefault(group, Map.of());
    }

    /** @return the names of the groups that have at least one strategy */
    public Set<String> groups() {
        return strategiesByGroup.keySet();
    }

    /** @return the configuration this registry was built with */
    protected SwitchboardProperties properties() {
        return properties;
    }
}
