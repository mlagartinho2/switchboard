package com.llizzard.switchboard;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * A signal that can identify which use case of a dispatch group should handle a request.
 *
 * <p>Register implementations as ordinary Spring beans. For each request, the group's resolvers
 * are consulted in the configured {@code switchboard.groups.<group>.resolution-order}, or in bean
 * order ({@code @Order}) when none is configured. The first resolver that produces a key wins.
 *
 * <p>Rules:
 * <ul>
 *   <li>A resolver belongs to exactly one group; it is never shared between groups.</li>
 *   <li>{@link #name()} must be non-blank and unique within the group (checked at startup). It
 *       is the name used in {@code resolution-order}.</li>
 *   <li>An empty, blank, or whitespace-only result means "no key, try the next resolver".</li>
 *   <li>Exceptions thrown by {@link #resolve} propagate to the caller unchanged.</li>
 * </ul>
 *
 * @param <Req> the group's request payload type
 */
public interface SwitchboardResolver<Req> {

    /**
     * Returns the single dispatch group this resolver belongs to.
     *
     * @return the single dispatch group this resolver belongs to
     */
    SwitchboardGroup<Req, ?> group();

    /**
     * Returns the resolver name; non-blank and unique within the group.
     *
     * @return the resolver name; non-blank and unique within the group
     */
    String name();

    /**
     * Tries to identify the use-case key for a request.
     *
     * @param httpRequest the current HTTP request
     * @param payload the request payload; may be {@code null}
     * @return the use-case key, or empty (or blank) when this resolver cannot tell
     */
    Optional<String> resolve(HttpServletRequest httpRequest, Req payload);
}
