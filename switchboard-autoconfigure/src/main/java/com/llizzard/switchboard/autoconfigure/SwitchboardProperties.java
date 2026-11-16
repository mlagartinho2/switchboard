package com.llizzard.switchboard.autoconfigure;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Switchboard configuration, bound from {@code switchboard.*} once at startup. Changes require a
 * restart.
 *
 * <p>Write case keys in bracket notation, for example
 * {@code switchboard.groups.refunds.cases.[REFUND_STANDARD].enabled=false}: a bracketed key binds
 * exactly as written. Keys are matched case-sensitively, and environment variables lower-case
 * them, so set per-case values in a configuration file or {@code SPRING_APPLICATION_JSON}.
 *
 * @param enabled whether Switchboard auto-configuration is active (default {@code true})
 * @param groups settings per dispatch group, keyed by group name
 */
@ConfigurationProperties("switchboard")
public record SwitchboardProperties(@DefaultValue("true") boolean enabled, Map<String, GroupSettings> groups) {

    private static final GroupSettings DEFAULT_GROUP = new GroupSettings(null, null);

    /**
     * Takes a defensive, unmodifiable copy of {@code groups}; {@code null} becomes empty.
     */
    public SwitchboardProperties {
        groups = (groups == null) ? Map.of() : Map.copyOf(groups);
    }

    /**
     * Returns the settings for a group.
     *
     * @param name the group name
     * @return the group's settings, or empty default settings for an unconfigured group; never null
     */
    public GroupSettings group(String name) {
        return groups.getOrDefault(name, DEFAULT_GROUP);
    }

    /**
     * Returns whether a use case is enabled in configuration.
     *
     * @param group the group name
     * @param key the use-case key, compared case-sensitively
     * @return {@code false} only when the case is configured with {@code enabled: false}
     */
    public boolean isEnabled(String group, String key) {
        CaseSettings settings = group(group).cases().get(key);
        return settings == null || settings.enabled();
    }

    /**
     * Settings for one dispatch group ({@code switchboard.groups.<name>}).
     *
     * @param resolutionOrder resolver names to consult, in order; {@code null} when absent, which
     *        means all of the group's resolvers in bean order
     * @param cases settings per use-case key
     */
    public record GroupSettings(List<String> resolutionOrder, Map<String, CaseSettings> cases) {

        /**
         * Takes defensive, unmodifiable copies; an absent {@code resolutionOrder} stays
         * {@code null}, and absent {@code cases} become empty.
         */
        public GroupSettings {
            resolutionOrder = (resolutionOrder == null) ? null : List.copyOf(resolutionOrder);
            cases = (cases == null) ? Map.of() : Map.copyOf(cases);
        }
    }

    /**
     * Settings for one use case ({@code switchboard.groups.<name>.cases.[<key>]}).
     *
     * @param enabled kill switch; {@code false} makes the key answer 404 (default {@code true})
     * @param owner optional owner, shown in the report
     * @param version optional version, shown in the report
     * @param description optional description, shown in the report
     */
    public record CaseSettings(@DefaultValue("true") boolean enabled, String owner, String version,
            String description) {
    }
}
