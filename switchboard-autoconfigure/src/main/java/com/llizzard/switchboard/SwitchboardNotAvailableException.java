package com.llizzard.switchboard;

import java.util.Objects;

/**
 * A use-case key was identified, but no enabled use case is registered for it in the group.
 * Mapped to HTTP 404 ({@code USE_CASE_NOT_AVAILABLE}).
 */
public final class SwitchboardNotAvailableException extends SwitchboardDispatchException {

    private static final long serialVersionUID = 1L;

    /** The resolved use-case key; part of the serialized form. */
    private final String caseKey;

    /**
     * Creates the exception for a key that has no enabled strategy in its group.
     *
     * @param group the dispatch group name
     * @param caseKey the key that was resolved
     * @throws NullPointerException if either argument is null
     */
    public SwitchboardNotAvailableException(String group, String caseKey) {
        super(group, "Use case '" + Objects.requireNonNull(caseKey, "caseKey must not be null")
                + "' in group '" + group + "' is not registered or is disabled.");
        this.caseKey = caseKey;
    }

    /**
     * Returns the use-case key that was resolved.
     *
     * @return the use-case key that was resolved
     */
    public String caseKey() {
        return caseKey;
    }
}
