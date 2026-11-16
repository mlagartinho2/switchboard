package com.llizzard.switchboard;

import java.util.Objects;

/**
 * Describes how a dispatch was routed. Passed to {@link SwitchboardStrategy#execute}.
 *
 * @param group the dispatch group name
 * @param caseKey the use-case key that was resolved
 * @param resolverName the name of the resolver that produced the key
 */
public record SwitchboardContext(String group, String caseKey, String resolverName) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if any component is null
     */
    public SwitchboardContext {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(caseKey, "caseKey must not be null");
        Objects.requireNonNull(resolverName, "resolverName must not be null");
    }
}
