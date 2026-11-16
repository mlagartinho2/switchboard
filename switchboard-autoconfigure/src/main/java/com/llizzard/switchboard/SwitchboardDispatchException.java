package com.llizzard.switchboard;

import java.util.Objects;

/**
 * A dispatch could not reach a use case. Exactly two outcomes exist, and they are never merged:
 * <ul>
 *   <li>{@link SwitchboardUnresolvedException}: no resolver identified a key (HTTP 501).</li>
 *   <li>{@link SwitchboardNotAvailableException}: a key was identified, but no enabled use case
 *       is registered for it (HTTP 404).</li>
 * </ul>
 *
 * <p>Messages name the group, and the key where there is one. They never include payload
 * contents.
 */
public abstract sealed class SwitchboardDispatchException extends RuntimeException
        permits SwitchboardUnresolvedException, SwitchboardNotAvailableException {

    private static final long serialVersionUID = 1L;

    /** The dispatch group name; part of the serialized form. */
    private final String group;

    SwitchboardDispatchException(String group, String message) {
        super(message);
        this.group = Objects.requireNonNull(group, "group must not be null");
    }

    /**
     * Returns the dispatch group name.
     *
     * @return the dispatch group name
     */
    public String group() {
        return group;
    }
}
