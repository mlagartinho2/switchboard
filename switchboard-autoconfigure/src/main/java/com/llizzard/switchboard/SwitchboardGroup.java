package com.llizzard.switchboard;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Typed handle for a dispatch group: one request/response contract served by several use cases.
 *
 * <p>Declare one constant per contract and share it between strategies, resolvers, and the
 * endpoints that dispatch to the group:
 *
 * <pre>{@code
 * public static final SwitchboardGroup<RefundRequest, RefundResponse> REFUNDS =
 *         SwitchboardGroup.of("refunds", RefundRequest.class, RefundResponse.class);
 * }</pre>
 *
 * <p>The name is also the configuration key under {@code switchboard.groups.<name>}, so it must
 * be lower-case kebab-case ({@code [a-z0-9]+(-[a-z0-9]+)*}). Two handles are equal only when the
 * name and both types are equal.
 *
 * @param <Req> the request payload type of the group's contract
 * @param <Res> the response type of the group's contract
 */
public final class SwitchboardGroup<Req, Res> {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final String name;
    private final Class<Req> requestType;
    private final Class<Res> responseType;

    private SwitchboardGroup(String name, Class<Req> requestType, Class<Res> responseType) {
        this.name = name;
        this.requestType = requestType;
        this.responseType = responseType;
    }

    /**
     * Creates a group handle, validating every argument eagerly.
     *
     * @param name lower-case kebab-case group name
     * @param <Req> the request payload type
     * @param <Res> the response type
     * @param requestType the request payload type
     * @param responseType the response type
     * @return the group handle
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code name} is not lower-case kebab-case
     */
    public static <Req, Res> SwitchboardGroup<Req, Res> of(
            String name, Class<Req> requestType, Class<Res> responseType) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(requestType, "requestType must not be null");
        Objects.requireNonNull(responseType, "responseType must not be null");
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid Switchboard group name '" + name
                    + "': must be lower-case kebab-case matching " + NAME_PATTERN.pattern());
        }
        return new SwitchboardGroup<>(name, requestType, responseType);
    }

    /**
     * Returns the group name, also its configuration key.
     *
     * @return the group name, also its configuration key
     */
    public String name() {
        return name;
    }

    /**
     * Returns the request payload type.
     *
     * @return the request payload type
     */
    public Class<Req> requestType() {
        return requestType;
    }

    /**
     * Returns the response type.
     *
     * @return the response type
     */
    public Class<Res> responseType() {
        return responseType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof SwitchboardGroup<?, ?> other
                && name.equals(other.name)
                && requestType.equals(other.requestType)
                && responseType.equals(other.responseType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, requestType, responseType);
    }

    /**
     * Returns the group name.
     *
     * @return the group name
     */
    @Override
    public String toString() {
        return name;
    }
}
