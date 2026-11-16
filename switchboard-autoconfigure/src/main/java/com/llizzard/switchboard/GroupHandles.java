package com.llizzard.switchboard;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Rule V8: one group name must always be declared with the same request and response types. */
final class GroupHandles {

    private GroupHandles() {
    }

    /**
     * Checks that every handle sharing a name is equal (same name and types).
     *
     * @param handles the group handles to check; nulls are not allowed
     * @param source what the handles came from, for the error message
     * @return the distinct handles, by group name
     * @throws IllegalStateException if two handles share a name but differ in types
     */
    static Map<String, SwitchboardGroup<?, ?>> requireConsistent(
            Collection<? extends SwitchboardGroup<?, ?>> handles, String source) {
        Map<String, SwitchboardGroup<?, ?>> byName = new HashMap<>();
        for (SwitchboardGroup<?, ?> handle : handles) {
            SwitchboardGroup<?, ?> existing = byName.putIfAbsent(handle.name(), handle);
            if (existing != null && !existing.equals(handle)) {
                throw new IllegalStateException("Switchboard group '" + handle.name()
                        + "' is declared with different types by " + source + ": "
                        + describe(existing) + " and " + describe(handle));
            }
        }
        return Map.copyOf(byName);
    }

    static String describe(SwitchboardGroup<?, ?> handle) {
        return "<" + handle.requestType().getName() + ", " + handle.responseType().getName() + ">";
    }
}
