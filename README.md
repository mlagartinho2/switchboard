# Switchboard

Switchboard is a Spring Boot starter for use-case dispatch. One HTTP endpoint serves a whole
family of use cases that share a request and response contract. Small, ordered **resolvers**
decide which use case a request is, and a **strategy** bean runs it. The routing is set in
configuration, with no `if`/`switch` in controllers. Adding a use case means adding one class.

```text
POST /api/refunds ──► dispatcher ──► resolvers (in order) ──► key "EXPEDITED" ──► ExpeditedRefundStrategy
```

## Support matrix

| Switchboard | Spring Boot | Java | Web stack |
|---|---|---|---|
| 2.0.0 | 4.x | 17+ | Servlet (Spring MVC) |

Switchboard activates only in a servlet web application. Reactive (WebFlux) applications are not
supported.

## Add the dependency

```xml
<dependency>
    <groupId>com.llizzard</groupId>
    <artifactId>switchboard-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>
```

The starter brings no third-party libraries beyond Spring Boot's own. The
[`/actuator/switchboard`](#inspection-endpoint) report is added only when
`spring-boot-starter-actuator` is also on the classpath.

## Walkthrough

The [sample application](switchboard-sample) contains the complete code for this walkthrough.

### 1. Declare a group

A group is one contract: a name plus the request and response types. Declare it once, as a
constant.

```java
public final class RefundGroup {

    public static final SwitchboardGroup<RefundRequest, RefundResponse> REFUNDS =
            SwitchboardGroup.of("refunds", RefundRequest.class, RefundResponse.class);

    private RefundGroup() {
    }
}
```

Group names are lower-case kebab-case (`[a-z0-9]+(-[a-z0-9]+)*`). The name is also the group's
configuration key.

### 2. Write one strategy per use case

```java
@Component
public class ExpeditedRefundStrategy implements SwitchboardStrategy<RefundRequest, RefundResponse> {

    @Override
    public SwitchboardGroup<RefundRequest, RefundResponse> group() {
        return RefundGroup.REFUNDS;
    }

    @Override
    public String caseKey() {
        return "EXPEDITED";
    }

    @Override
    public RefundResponse execute(RefundRequest request, SwitchboardContext context) {
        return new RefundResponse("ExpeditedRefundStrategy", "REFUNDED", 4);
    }
}
```

Case keys match `[A-Za-z0-9_-]+`, are case-sensitive, and must be unique within a group. The same
key in two different groups names two independent use cases.

### 3. Write at least one resolver per group

A resolver reads a signal (a header, the payload, the caller) and returns a case key, or
`Optional.empty()` to let the next resolver decide.

```java
@Component
@Order(1)
public class RefundTypeHeaderResolver implements SwitchboardResolver<RefundRequest> {

    @Override
    public SwitchboardGroup<RefundRequest, ?> group() {
        return RefundGroup.REFUNDS;
    }

    @Override
    public String name() {
        return "refund-type-header";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest httpRequest, RefundRequest payload) {
        return Optional.ofNullable(httpRequest.getHeader("X-Refund-Type"));
    }
}
```

Resolvers are consulted in `@Order`, and the first non-empty key wins. Resolver names must be
unique within a group, because `resolution-order` refers to them.

### 4. Make the controller one line

```java
@RestController
public class RefundController {

    private final SwitchboardDispatcher dispatcher;

    public RefundController(SwitchboardDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/api/refunds")
    public RefundResponse refund(HttpServletRequest http, @RequestBody RefundRequest body) {
        return dispatcher.dispatch(RefundGroup.REFUNDS, http, body);
    }
}
```

The controller never changes when use cases are added, removed, or switched off.

### Startup checks

A misconfiguration stops the application at startup, with a message that names the group and the
offending key, resolver, or class. Examples:
- a duplicate case key or resolver name in a group;
- a group that has strategies but no resolvers;
- an unknown name in `resolution-order`.

The full list is in [data-model.md](specs/001-use-case-dispatch/data-model.md), rules V1–V9.

## Configuration

Every setting is read once at startup, so a change takes effect on restart or redeploy.

```yaml
switchboard:
  enabled: true                        # false: Switchboard registers no beans at all
  groups:
    refunds:
      resolution-order: [refund-type-header, refund-amount]   # optional
      cases:
        "[STANDARD]":
          owner: payments-team         # metadata, shown in the inspection report
          version: "1.0"
          description: Standard refund flow
        "[EXPEDITED]":
          enabled: false               # kill switch: this case now answers 404
```

- **Kill switch.** `cases."[KEY]".enabled: false` makes that key answer 404 without a code change.
  Only that key in that group is affected.
- **Resolution order.** If `resolution-order` is absent, every resolver in the group is consulted
  in `@Order`. If it is present, only the named resolvers are consulted, in that order. An empty
  list or an unknown name stops startup.
- **Bracket notation for case keys.** Always write case keys in brackets, for example
  `"[REFUND_STANDARD]"`. Brackets make Spring Boot keep the key exactly as written; without them,
  characters other than letters, digits, `-`, and `_` are dropped. Case keys are matched exactly
  and case-sensitively. A configured key that matches no strategy logs a startup WARN.
- **Environment variables** lower-case map keys, so they cannot set a case key reliably. To
  override one case from the environment, use `SPRING_APPLICATION_JSON`, for example:

  ```bash
  SPRING_APPLICATION_JSON='{"switchboard":{"groups":{"refunds":{"cases":{"[EXPEDITED]":{"enabled":false}}}}}}'
  ```

  An external configuration file also works.

The full reference is in
[contracts/configuration.md](specs/001-use-case-dispatch/contracts/configuration.md).

## Error responses

When dispatch cannot route a request, Switchboard answers with an RFC 9457 problem response
(`application/problem+json`). This needs no code in your application, and it takes precedence
over your own catch-all `@ExceptionHandler`.

| Situation | Status | `errorCode` |
|---|---|---|
| No resolver identified a use case | 501 | `USE_CASE_UNRESOLVED` |
| The key is not registered in the group, or is disabled | 404 | `USE_CASE_NOT_AVAILABLE` (adds `key`) |

```json
{
  "type": "tag:llizzard.com,2026:switchboard:use-case-not-available",
  "title": "Use case not available",
  "status": 404,
  "detail": "Use case 'EXPEDITED' in group 'refunds' is not registered or is disabled.",
  "instance": "/api/refunds",
  "errorCode": "USE_CASE_NOT_AVAILABLE",
  "group": "refunds",
  "key": "EXPEDITED"
}
```

Responses never include payload data. The resolved key is echoed in `key` and `detail` only when
it matches the key format and is at most 64 characters; otherwise both leave it out. Exceptions
thrown by your own strategies and resolvers are not converted: they reach your normal error
handling unchanged.

What clients can rely on:
- `type`, `errorCode`, `status`, `group`, and `key` are stable. Match on `type` or `errorCode`.
- `title` and `detail` are text for people and may change in any release; don't parse them.
- Later versions may add new error codes and new fields. Ignore fields you don't know, and treat
  an unknown `errorCode` like any other error with that status.

The full contract is in
[contracts/http-errors.md](specs/001-use-case-dispatch/contracts/http-errors.md).

## Inspection endpoint

With actuator on the classpath, `GET /actuator/switchboard` reports each group's effective
resolver order and every registered or configured use case. For each use case, it shows whether
the case is enabled, whether a strategy is registered, the strategy class, and the owner,
version, and description. Like every custom endpoint, it is served only once you expose it:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, switchboard
```

> **Security:** the report describes your routing internals. Treat it like `/actuator/env`, and
> restrict it to operators, for example with Spring Security or a separate management port.
> `management.endpoint.switchboard.access: none` removes it.

The response schema is in
[contracts/actuator-endpoint.md](specs/001-use-case-dispatch/contracts/actuator-endpoint.md).

## Turning Switchboard off

`switchboard.enabled: false` makes Switchboard register no beans at all, including no dispatcher.
Your strategies and resolvers are plain beans and still load harmlessly.

A controller that injects `SwitchboardDispatcher` directly will then stop the application from
starting. If the application must run with Switchboard off, do one of the following:

- Make the controller back off with the library:

  ```java
  @RestController
  @ConditionalOnProperty(prefix = "switchboard", name = "enabled", havingValue = "true", matchIfMissing = true)
  public class RefundController { ... }
  ```

- Or inject `ObjectProvider<SwitchboardDispatcher>`, and decide what the endpoint returns when it
  is absent.

## Replacing a part

Every Switchboard bean is created with `@ConditionalOnMissingBean`. If you define your own bean of
one of these types, Switchboard uses yours and does not create its own:
- `SwitchboardRegistry`
- `SwitchboardResolverChain`
- `SwitchboardDispatcher`
- `SwitchboardStartupValidator`
- `SwitchboardExceptionHandler`
- `SwitchboardEndpoint`

The classes are public and non-final, so you can subclass one to change a single behavior. For
example, subclassing `SwitchboardExceptionHandler` changes the error format.

## Building from source

```bash
mvn verify                                        # build and test every module
mvn -pl switchboard-sample -am spring-boot:run   # run the sample on localhost:8080
```

[quickstart.md](specs/001-use-case-dispatch/quickstart.md) lists the requests to try against the
running sample.

| Module | Contents |
|---|---|
| `switchboard-autoconfigure` | The library: API, dispatch, configuration, error handling, endpoint |
| `switchboard-spring-boot-starter` | The dependency to add; contains no code |
| `switchboard-sample` | A runnable example; not published |
