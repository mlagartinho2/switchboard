package com.llizzard.switchboard.actuate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The read-only routing report served by {@link SwitchboardEndpoint}
 * ({@code GET /actuator/switchboard}). Immutable.
 *
 * @param groups one entry per group, sorted by group name
 */
public record SwitchboardReport(Map<String, GroupReport> groups) {

    /**
     * Copies {@code groups} into an unmodifiable map sorted by group name.
     *
     * @throws NullPointerException if {@code groups} is null
     */
    public SwitchboardReport {
        groups = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(groups, "groups must not be null")));
    }

    /**
     * One group in the report.
     *
     * @param resolutionOrder the effective resolver order: configured, or else bean order
     * @param cases registered and configured use cases, sorted by key
     */
    public record GroupReport(List<String> resolutionOrder, List<CaseReport> cases) {

        /**
         * Takes defensive, unmodifiable copies of both lists.
         *
         * @throws NullPointerException if either list is null
         */
        public GroupReport {
            resolutionOrder = List.copyOf(Objects.requireNonNull(resolutionOrder, "resolutionOrder must not be null"));
            cases = List.copyOf(Objects.requireNonNull(cases, "cases must not be null"));
        }
    }

    /**
     * One use case in a group. {@code enabled} and {@code registered} are reported separately on
     * purpose: a case can be disabled but registered, or configured but never deployed.
     *
     * @param key the use-case key
     * @param enabled the configured kill switch (default {@code true})
     * @param registered whether a strategy bean exists for the key
     * @param strategyClass the strategy's user class name (no proxy suffix), or {@code null} when
     *        not registered
     * @param owner configured owner, or {@code null}
     * @param version configured version, or {@code null}
     * @param description configured description, or {@code null}
     */
    public record CaseReport(String key, boolean enabled, boolean registered, String strategyClass, String owner,
            String version, String description) {

        /**
         * Validates the key.
         *
         * @throws NullPointerException if {@code key} is null
         */
        public CaseReport {
            Objects.requireNonNull(key, "key must not be null");
        }
    }
}
