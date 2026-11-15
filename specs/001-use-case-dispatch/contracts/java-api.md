# Contract: Public Java API (`com.llizzard.switchboard`)

These are the types a consuming developer codes against. Signatures are normative. Method bodies
are left to implementation. All types live in `switchboard-autoconfigure`, and consumers get them
through `switchboard-spring-boot-starter`. Changing anything here after 2.0.0 is a semver-major
change.

## Consumer-implemented types

```java
public interface SwitchboardStrategy<Req, Res> {
    SwitchboardGroup<Req, Res> group();          // the contract this use case serves
    String caseKey();                            // [A-Za-z0-9_-]+, unique within the group
    Res execute(Req request, SwitchboardContext context);
}

public interface SwitchboardResolver<Req> {      // the spec's "signal"
    SwitchboardGroup<Req, ?> group();            // exactly one group
    String name();                               // unique within the group; used in resolution-order
    Optional<String> resolve(HttpServletRequest httpRequest, Req payload);
    // empty or blank → "no key, try the next resolver"; exceptions propagate unchanged
}
```

Both are registered as ordinary Spring beans. Resolvers that should be consulted first by
default carry `@Order` (see research §R6).

## Library-provided value types

```java
public final class SwitchboardGroup<Req, Res> {
    public static <Req, Res> SwitchboardGroup<Req, Res> of(
            String name, Class<Req> requestType, Class<Res> responseType); // validates eagerly
    public String name();
    public Class<Req> requestType();
    public Class<Res> responseType();
    // equals/hashCode over name + both types; toString = name
}

public record SwitchboardContext(String group, String caseKey, String resolverName) { }
public record SwitchboardResolution(String caseKey, String resolverName) { }
```

## Library-provided services (Spring beans, all `@ConditionalOnMissingBean`)

```java
public class SwitchboardDispatcher {
    public SwitchboardDispatcher(SwitchboardResolverChain chain, SwitchboardRegistry registry);
    // requireNonNull on both

    public <Req, Res> Res dispatch(SwitchboardGroup<Req, Res> group,
                                   HttpServletRequest httpRequest,
                                   Req request);
    // group, httpRequest: required (NPE with message). request: may be null (e.g. GET).
    // throws SwitchboardUnresolvedException, SwitchboardNotAvailableException;
    // strategy/resolver exceptions propagate unchanged.
}

public class SwitchboardResolverChain {
    public SwitchboardResolverChain(List<SwitchboardResolver<?>> orderedResolvers,
                                    SwitchboardProperties properties);  // copies; validates V3–V5, V8
    public <Req> Optional<SwitchboardResolution> resolve(SwitchboardGroup<Req, ?> group,
                                                         HttpServletRequest httpRequest,
                                                         Req payload);
    public List<String> resolutionOrder(String group);   // empty list for unknown group
    public Set<String> groups();
}

public class SwitchboardRegistry {
    public SwitchboardRegistry(List<SwitchboardStrategy<?, ?>> strategies,
                               SwitchboardProperties properties);  // copies; validates V1, V2, V8
    public Optional<SwitchboardStrategy<?, ?>> find(String group, String caseKey); // enabled only
    public Map<String, SwitchboardStrategy<?, ?>> registered(String group);     // incl. disabled; empty if none
    public boolean isEnabled(String group, String caseKey);
    public Set<String> groups();
}
```

**The two unchecked casts** (Principle V, Item 27). Each is on a local variable only, with a
one-line safety comment. No other `@SuppressWarnings("unchecked")` is allowed in the library
(audited by T062).

1. In `SwitchboardDispatcher.dispatch`, the strategy found in the registry:

   ```java
   // Safe: the registry is keyed by (group name, key), and startup validation (V8) guarantees every
   // strategy under this group name was compiled against SwitchboardGroup<Req, Res>.
   @SuppressWarnings("unchecked")
   SwitchboardStrategy<Req, Res> strategy = (SwitchboardStrategy<Req, Res>) found;
   ```

2. In `SwitchboardResolverChain.resolve`, each resolver of the group being resolved:

   ```java
   // Safe: resolvers are grouped by group name, and startup validation (V8) guarantees every
   // resolver under this group name was compiled against SwitchboardGroup<Req, ?>.
   @SuppressWarnings("unchecked")
   SwitchboardResolver<Req> typed = (SwitchboardResolver<Req>) resolver;
   ```

## Exceptions

```java
public abstract sealed class SwitchboardDispatchException extends RuntimeException
        permits SwitchboardUnresolvedException, SwitchboardNotAvailableException {
    public String group();
}
public final class SwitchboardUnresolvedException extends SwitchboardDispatchException { }
public final class SwitchboardNotAvailableException extends SwitchboardDispatchException {
    public String caseKey();
}
```

The messages name the group, and the key where there is one. They never include payload
contents. HTTP mapping: [http-errors.md](http-errors.md).

## Typical consumer usage (illustrative, not library code)

```java
public final class RefundGroup {
    public static final SwitchboardGroup<RefundRequest, RefundResponse> REFUNDS =
            SwitchboardGroup.of("refunds", RefundRequest.class, RefundResponse.class);
}

@PostMapping("/api/refunds")
RefundResponse refund(HttpServletRequest http, @RequestBody RefundRequest body) {
    return dispatcher.dispatch(RefundGroup.REFUNDS, http, body);   // no per-use-case branching
}
```

## Overridable beans (FR-029 / SC-007)

| Bean type | Default provided by |
|---|---|
| `SwitchboardRegistry` | `SwitchboardAutoConfiguration` |
| `SwitchboardResolverChain` | `SwitchboardAutoConfiguration` |
| `SwitchboardDispatcher` | `SwitchboardAutoConfiguration` |
| `SwitchboardStartupValidator` | `SwitchboardAutoConfiguration` |
| `SwitchboardExceptionHandler` | `SwitchboardAutoConfiguration` |
| `SwitchboardEndpoint` | `SwitchboardEndpointAutoConfiguration` |

A consumer bean of the same type replaces the default. Each row has one override test.
