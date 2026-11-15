package com.llizzard.switchboard;

/**
 * One use case of a dispatch group: the consumer's business logic for one way of handling the
 * group's contract.
 *
 * <p>Register implementations as ordinary Spring beans. The library finds them at startup and
 * runs the one whose {@link #caseKey()} the group's resolvers produce for a request.
 *
 * <p>Rules, checked at startup:
 * <ul>
 *   <li>{@link #caseKey()} must match {@code [A-Za-z0-9_-]+}.</li>
 *   <li>{@link #caseKey()} must be unique within the group. The same key may be used in other
 *       groups; keys are compared case-sensitively.</li>
 *   <li>{@link #group()} must return the same handle on every call.</li>
 * </ul>
 *
 * @param <Req> the group's request payload type
 * @param <Res> the group's response type
 */
public interface SwitchboardStrategy<Req, Res> {

    /** @return the dispatch group (contract) this use case serves */
    SwitchboardGroup<Req, Res> group();

    /**
     * @return the use-case key; matches {@code [A-Za-z0-9_-]+} and is unique within the group
     */
    String caseKey();

    /**
     * Runs the use case. Exceptions propagate to the caller unchanged.
     *
     * @param request the request payload; may be {@code null} (for example on a GET)
     * @param context which group, key, and resolver selected this use case
     * @return the response
     */
    Res execute(Req request, SwitchboardContext context);
}
