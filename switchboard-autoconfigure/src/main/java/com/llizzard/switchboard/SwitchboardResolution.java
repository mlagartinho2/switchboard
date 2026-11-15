package com.llizzard.switchboard;

import java.util.Objects;

/**
 * The result of a successful resolution: the key a resolver produced, and which resolver it was.
 * The key is passed through unchanged (not trimmed or case-folded).
 *
 * @param caseKey the use-case key
 * @param resolverName the name of the resolver that produced the key
 */
public record SwitchboardResolution(String caseKey, String resolverName) {

    public SwitchboardResolution {
        Objects.requireNonNull(caseKey, "caseKey must not be null");
        Objects.requireNonNull(resolverName, "resolverName must not be null");
    }
}
