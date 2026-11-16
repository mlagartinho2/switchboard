package com.llizzard.switchboard;

/**
 * No resolver in the group identified a use case for the request. Mapped to HTTP 501
 * ({@code USE_CASE_UNRESOLVED}).
 */
public final class SwitchboardUnresolvedException extends SwitchboardDispatchException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception for a group in which no resolver produced a key.
     *
     * @param group the dispatch group name
     * @throws NullPointerException if {@code group} is null
     */
    public SwitchboardUnresolvedException(String group) {
        super(group, "No resolver in group '" + group + "' identified a use case for this request.");
    }
}
