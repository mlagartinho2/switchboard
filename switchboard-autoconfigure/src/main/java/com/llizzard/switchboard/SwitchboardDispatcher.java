package com.llizzard.switchboard;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Routes a request to the use case its group's resolvers identify.
 *
 * <pre>{@code
 * @PostMapping("/api/refunds")
 * RefundResponse refund(HttpServletRequest http, @RequestBody RefundRequest body) {
 *     return dispatcher.dispatch(RefundGroup.REFUNDS, http, body);
 * }
 * }</pre>
 *
 * <p>Each dispatch is logged once at DEBUG on this class's logger with the group, key, resolver,
 * outcome ({@code handled}, {@code not_found}, {@code not_implemented}, {@code failed}), and
 * elapsed microseconds. The payload is never logged.
 *
 * <p>Public and non-final so a consuming application can replace it (FR-029); a replacement
 * takes over the logging too.
 */
public class SwitchboardDispatcher {

    private static final Log logger = LogFactory.getLog(SwitchboardDispatcher.class);
    private static final String NONE = "-";

    private final SwitchboardResolverChain chain;
    private final SwitchboardRegistry registry;

    /**
     * @param chain the resolver chain
     * @param registry the strategy registry
     * @throws NullPointerException if an argument is null
     */
    public SwitchboardDispatcher(SwitchboardResolverChain chain, SwitchboardRegistry registry) {
        this.chain = Objects.requireNonNull(chain, "chain must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Resolves the use-case key for the request and runs the matching strategy.
     *
     * @param group the group handle
     * @param httpRequest the current HTTP request
     * @param request the request payload; may be null (for example on a GET)
     * @return the strategy's response
     * @throws SwitchboardUnresolvedException if no resolver identified a key
     * @throws SwitchboardNotAvailableException if no strategy is available for the key
     * @throws NullPointerException if {@code group} or {@code httpRequest} is null
     */
    public <Req, Res> Res dispatch(SwitchboardGroup<Req, Res> group, HttpServletRequest httpRequest, Req request) {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(httpRequest, "httpRequest must not be null");
        long start = System.nanoTime();
        String key = NONE;
        String resolver = NONE;
        String outcome = "failed";
        try {
            Optional<SwitchboardResolution> resolution = chain.resolve(group, httpRequest, request);
            if (resolution.isEmpty()) {
                outcome = "not_implemented";
                throw new SwitchboardUnresolvedException(group.name());
            }
            key = resolution.get().caseKey();
            resolver = resolution.get().resolverName();
            Optional<SwitchboardStrategy<?, ?>> found = registry.find(group.name(), key);
            if (found.isEmpty()) {
                outcome = "not_found";
                throw new SwitchboardNotAvailableException(group.name(), key);
            }
            if (!group.equals(found.get().group())) {
                throw new IllegalArgumentException("Switchboard group '" + group.name() + "' was dispatched with types "
                        + GroupHandles.describe(group) + " but strategy " + found.get().getClass().getName()
                        + " uses " + GroupHandles.describe(found.get().group()));
            }
            // Safe: the registry is keyed by (group name, key), and startup validation (V8) guarantees every
            // strategy under this group name was compiled against SwitchboardGroup<Req, Res>.
            @SuppressWarnings("unchecked")
            SwitchboardStrategy<Req, Res> strategy = (SwitchboardStrategy<Req, Res>) found.get();
            Res response = strategy.execute(request, new SwitchboardContext(group.name(), key, resolver));
            outcome = "handled";
            return response;
        }
        finally {
            if (logger.isDebugEnabled()) {
                long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
                logger.debug("Switchboard dispatch group=" + group.name() + " key=" + key + " resolver=" + resolver
                        + " outcome=" + outcome + " elapsedMicros=" + elapsedMicros);
            }
        }
    }
}
