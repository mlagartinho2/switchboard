# Phase 0 Research: Switchboard Use-Case Dispatch Library

Every open question in the plan's Technical Context is resolved below. Each entry gives the
decision, the rationale, and the alternatives considered. Items marked **verify at
implementation** are platform facts to confirm against the Spring Boot version actually pinned.
They do not affect the design.

## R1 — Platform baseline

- **Decision**: Use Spring Boot 4.x, pinned to the latest GA patch available when implementation
  starts (minimum 4.0.0), with Spring Framework 7, Jakarta EE 11 / Servlet 6.1, and Java 17
  (`--release 17`). The starter depends on `spring-boot-starter-webmvc`, Boot 4's name for the
  MVC starter.
- **Rationale**: Principle I fixes Boot 4.x and Java 17+. Principle VII forbids milestone and RC
  versions. Boot 4 split its auto-configuration into per-technology modules, so the
  autoconfigure module declares the specific Boot modules it uses rather than one monolithic
  artifact.
- **Alternatives considered**: `spring-boot-starter-parent` as the build parent was rejected.
  The library's parent pom must be published and should not force Boot's plugin defaults onto
  consumers, so the plan imports the `spring-boot-dependencies` BOM instead.
- **Verify at implementation**: the exact Boot 4 module artifact IDs for WebMVC, actuator
  autoconfigure, and test support. Also the package of `@ConditionalOnAvailableEndpoint` after
  Boot 4's modularization.

## R2 — How a dispatch group is identified and typed

- **Decision**: Introduce a typed handle, `SwitchboardGroup<Req, Res>`, holding a name and the
  request and response classes. Consumers declare it once per contract, for example
  `RefundGroup.REFUNDS`. Strategies and resolvers return it from `group()`, and endpoints pass it
  to `dispatch(...)`, so the return type is inferred as `Res`. Group names must be lower-case
  kebab-case (`[a-z0-9]+(-[a-z0-9]+)*`) so they bind cleanly as configuration map keys.
- **Rationale**: The compiler then ties each strategy to its group's contract. A
  `SwitchboardStrategy<RefundRequest, RefundResponse>` can only return a
  `SwitchboardGroup<RefundRequest, RefundResponse>`. This removes the "wrong payload shape"
  failure behind the groups clarification. The dispatcher's remaining unchecked cast is safe
  because every strategy in a group was compiled against the same group types, and startup
  validation rejects two handles that share a name but declare different types.
- **Alternatives considered**: Plain string group names would compile with any types, so a
  mismatch would only surface at request time as a `ClassCastException`, which was rejected. A
  group derived from the request class alone would break when two groups share a request type,
  which was also rejected.

## R3 — How strategies and resolvers register

- **Decision**: Strategies and resolvers are ordinary Spring beans (`@Component` or `@Bean`) that
  implement the library interfaces. `group()`, `caseKey()`, and `name()` are interface methods.
  The `@SwitchboardCase` annotation from the earlier draft is dropped.
- **Rationale**: Interface methods are type-checked, need no reflection, and work in unit tests
  without Spring (Testing section). The constitution already refers to `caseKey()`. An annotation
  would now need a group attribute too, and it would duplicate what the interface already states.
- **Alternatives considered**: A `@SwitchboardCase(group=…, key=…)` meta-annotated `@Component`
  was rejected because it creates two sources of truth that can disagree. Classpath scanning by
  the library itself was rejected because it is less overridable and harder to test.

## R4 — Kill-switch enforcement

- **Decision**: All strategy beans are created. `SwitchboardRegistry` reads
  `switchboard.groups.<group>.cases.<key>.enabled` (default `true`) and exposes only enabled
  strategies through `find(group, key)`. It reports registered strategies separately for the
  endpoint.
- **Rationale**: This follows the clarification ("the library enforces it") and FR-014/015. A
  disabled case returns 404 exactly like an unknown key, yet still shows as `registered: true` in
  the report, so an operator can tell "switched off" from "never deployed".
- **Alternatives considered**: `@ConditionalOnProperty` on each strategy, as in the earlier draft,
  was rejected. Every team would have to remember it and name the property consistently, and a
  disabled bean would vanish from the report entirely.

## R5 — Configuration binding of case keys

- **Decision**:
  - Configuration is a nested map:
    `switchboard.groups.<group>.resolution-order` (list) and
    `switchboard.groups.<group>.cases.<key>.{enabled, owner, version, description}`.
  - Case keys must match `[A-Za-z0-9_-]+`. This is validated when strategies register, and
    keys are compared case-sensitively.
  - Documentation and the sample use bracket notation for keys (`"[REFUND_STANDARD]":`).
  - At startup the library logs a WARN for any configured case key that matches no registered
    strategy in its group.
- **Rationale**: Spring Boot strips characters other than letters, digits, `-` and `.` from
  map keys unless the key is bracketed. Without brackets, `REFUND_STANDARD` binds as
  `REFUNDSTANDARD`, and the kill switch would silently miss. Brackets preserve the key exactly.
  The WARN catches the mistake, while still allowing configuration without an implementation
  (spec edge case). Environment variables cannot express case-sensitive or underscored map keys,
  so the documentation points operators to `SPRING_APPLICATION_JSON` or a config file for
  per-case overrides.
- **Alternatives considered**:
  - A list of `{key, enabled}` objects avoids key mangling, but profile overrides replace whole
    lists, so turning off one case would mean restating every case. Rejected.
  - Restricting keys to `[A-Za-z0-9-]` would forbid the common `UPPER_SNAKE` style. Rejected.
  - Failing startup on a configured key with no implementation would contradict the spec edge
    case "configuration without implementation". Rejected.
- **Verify at implementation**: a binding test confirms bracketed keys keep their case and
  underscores.

## R6 — Default resolver order

- **Decision**: When `resolution-order` is absent for a group, the library consults that group's
  resolvers in Spring's standard bean order (`@Order` / `Ordered`, then registration order), via
  `ObjectProvider.orderedStream()`. When it is present, only the listed names are consulted, in
  the listed order.
- **Rationale**: This is how consuming developers already express ordering in Spring, which makes
  it the "declared order" in the spec's assumption. Configuration overrides it without code
  changes (FR-013).
- **Alternatives considered**: Alphabetical by name is arbitrary and surprising. Requiring an
  explicit order for every group adds boilerplate for single-resolver groups. Both were rejected.

## R7 — Where startup validation runs

- **Decision**: Validation is split across three places:
  - `SwitchboardRegistry` constructor: duplicate keys per group (names both classes), key
    format, and consistent group types among strategies.
  - `SwitchboardResolverChain` constructor: duplicate resolver names per group, unknown names in
    a configured order, an explicitly empty order, and consistent group types among resolvers.
  - `SwitchboardStartupValidator` (`SmartInitializingSingleton`): the cross-component rules
    (below), run after all singletons exist.
  - All failures throw `IllegalStateException` with the group and offending names, which aborts
    startup.
- **Cross-component rules**: a group that has strategies but no resolvers; configuration naming a
  group that has neither strategies nor resolvers; the same group name declared with different
  types by a strategy and a resolver; and the WARN from §R5.
- **Rationale**: Constructor checks follow Effective Java Item 49 and keep each class
  self-validating in plain unit tests. Rules that need several components belong in one place
  that runs before the application accepts traffic. This covers every misconfiguration in SC-006
  at startup.
- **Alternatives considered**: Validating lazily on first request contradicts SC-006 and was
  rejected. A single "god" validator holding all rules would leave the registry and chain unsafe
  when used directly, and was also rejected.

## R8 — Failure responses

- **Decision**:
  - `SwitchboardExceptionHandler` is a `@RestControllerAdvice` at
    `Ordered.HIGHEST_PRECEDENCE`. It handles only `SwitchboardUnresolvedException` and
    `SwitchboardNotAvailableException`.
  - It returns Spring's `ProblemDetail` (RFC 9457, `application/problem+json`), with extra
    properties `errorCode`, `group`, and (for 404) `key`.
  - Error codes: `USE_CASE_UNRESOLVED` (501) and `USE_CASE_NOT_AVAILABLE` (404).
  - Type URIs: `urn:switchboard:error:use-case-unresolved` and
    `urn:switchboard:error:use-case-not-available`.
  - `instance` is the request path.
  - The handler is `@ConditionalOnMissingBean(SwitchboardExceptionHandler.class)`.
- **Rationale**:
  - This satisfies FR-036 with a platform-native type, so no new dependency is needed.
  - Advice beans are tried in order, so the highest precedence stops a consumer's catch-all
    `@ExceptionHandler(Exception.class)` from swallowing these two exceptions (FR-023). Because
    the handler covers only Switchboard exceptions, it cannot hijack anything else.
  - URNs avoid publishing a documentation site just to host type URIs.
  - The error codes become a public contract once 2.0.0 ships, and must not change afterward.
- **Alternatives considered**: The custom `ErrorResponse(code, message)` record from the earlier
  draft was superseded by the clarification. `about:blank` types were rejected because the type
  would not distinguish the two failures.

## R9 — Dispatch logging without AOP

- **Decision**: `SwitchboardDispatcher` logs each dispatch itself through SLF4J at DEBUG, using
  logger `com.llizzard.switchboard.SwitchboardDispatcher`. Each entry records the group, key (or
  `-`), resolver name (or `-`), outcome, and elapsed microseconds. Outcomes are `handled`,
  `not_found`, `not_implemented`, and `failed` (when a strategy throws; the exception is
  rethrown). The payload is never logged.
- **Rationale**: This meets FR-027 and the clarification ("logging only"). DEBUG is off under
  Boot's default INFO level. `@Aspect` advice, as in the earlier draft's "Decorator (Spring AOP)",
  needs `aspectjweaver`, a third-party library, which would require Principle VII research and
  approval for one log line. Consumers who replace the dispatcher (FR-029) take over its logging
  too.
- **Alternatives considered**: An AOP aspect was rejected (above). Micrometer metrics are out of
  scope per FR-028.

## R10 — Actuator endpoint

- **Decision**:
  - `SwitchboardEndpoint` (`@Endpoint(id = "switchboard")`, one `@ReadOperation`) is registered
    by a separate `SwitchboardEndpointAutoConfiguration`. That class has
    `@ConditionalOnClass(Endpoint.class)`, `@ConditionalOnBean(SwitchboardRegistry.class)`,
    `@ConditionalOnAvailableEndpoint`, and `@ConditionalOnMissingBean`.
  - Actuator autoconfigure is an `<optional>` dependency of `switchboard-autoconfigure`.
  - Access follows Boot's standard model: `management.endpoint.switchboard.access` (`none` /
    `read-only` / `unrestricted`), plus web exposure via
    `management.endpoints.web.exposure.include`.
- **Rationale**: This meets FR-026, because applications without actuator start normally and get
  no endpoint. Like every custom endpoint, it is not exposed over HTTP until the operator includes
  it. The `management.endpoint.<id>.enabled` property from the earlier draft is the older model,
  replaced by `access` from Boot 3.4 onward.
- **Alternatives considered**: Exposing the report as a plain MVC controller was rejected because
  it would bypass the platform's exposure and security conventions.
- **Verify at implementation**: the `access` property and its default for custom endpoints on the
  pinned Boot 4 version. Tests hide actuator with `FilteredClassLoader` to prove the back-off.

## R11 — Behavior when the library is switched off

- **Decision**: `switchboard.enabled=false` makes every Switchboard auto-configuration back off,
  so no beans are registered. Consumer strategies and resolvers are plain beans, so they still
  load harmlessly. Consumer controllers that must start while Switchboard is off should inject
  `ObjectProvider<SwitchboardDispatcher>` or carry
  `@ConditionalOnProperty(name = "switchboard.enabled", matchIfMissing = true)`. The quickstart
  documents both.
- **Rationale**: FR-017 says the library "provides no dispatch behavior" when it is off. A
  substitute dispatcher that always answers 501 would be dispatch behavior, and it would hide the
  misconfiguration.
- **Alternatives considered**: A no-op dispatcher bean was rejected (above). Failing startup when
  something injects the dispatcher while it is off was also rejected: this is Spring's normal
  missing-bean failure, which already names the dependency.
- **Note for the user**: if a consumer controller injects `SwitchboardDispatcher` directly, the
  application only starts with the switch off if the controller uses one of the two patterns
  above. FR-017 is met by the library, and that one obligation sits with the consumer.

## R12 — Test approach and the SC-008 performance check

- **Decision**:
  - Unit tests (JUnit Jupiter, AssertJ, Mockito, `MockHttpServletRequest`) for every core class,
    with no Spring context.
  - `WebApplicationContextRunner` for auto-configuration, back-off, each
    `@ConditionalOnMissingBean` override (SC-007), and each startup failure (SC-006).
  - `MockMvc` tests in the sample module for the end-to-end success criteria.
  - SC-008 is checked by a plain JUnit test, `SwitchboardDispatchPerformanceTest`. It calls the
    dispatcher with stub strategies and resolvers, runs 10k warm-up and 100k measured
    iterations, and asserts p95 < 1 ms.
- **Rationale**: This matches the constitution's Testing section. The performance bound is about
  three orders of magnitude above the expected cost, so a simple timer is stable enough without a
  benchmarking harness.
- **Alternatives considered**: JMH is the accurate tool for microbenchmarks, but it is a
  third-party library and would need Principle VII research and approval. It is not adopted. If
  precise numbers are needed later, it can be proposed through that process.

## R13 — Build and publishing

- **Decision**:
  - Maven, with a published `switchboard-parent` pom (group `com.llizzard`, version `2.0.0`) that
    imports the Spring Boot BOM and sets `maven.compiler.release=17`. Its modules are
    `switchboard-autoconfigure`, `switchboard-spring-boot-starter`, and `switchboard-sample`
    (`maven.deploy.skip=true`).
  - `distributionManagement` points at the internal Nexus `maven-releases` repository.
  - Consumers depend only on the starter.
- **Rationale**: This meets Principle I (lockstep) and the Publishing section. The parent must be
  published because both artifacts inherit from it.
- **Open item (not blocking)**: the internal Nexus URL is not known. It is a build-configuration
  value to supply before the first release.
- **Alternatives considered**: Gradle was rejected because the inputs and conventions use Maven
  (`pom.xml`). A separate repository for the sample was rejected because the sample is the
  library's end-to-end test and belongs in the same build.

## R14 — Differences from the earlier draft (`copilot/input/plan.md`)

| Draft element | Decision now | Reason |
|---|---|---|
| Built-in `HeaderResolver`, `PayloadFieldResolver`, `RouteResolver`; `header-name` setting | None shipped | Spec clarification: no built-in signals (FR-008) |
| One global resolver chain and flat `switchboard.<case>.enabled` keys | Per-group chains and `switchboard.groups.<group>.cases.<key>.enabled` | Spec clarification: dispatch groups (FR-032–035) |
| `@SwitchboardCase` plus `@ConditionalOnProperty` per strategy | Interface methods; registry enforces enablement | §R3, §R4 |
| `dispatch(http, request)` with untyped return | `dispatch(group, http, request)` returning the group's `Res` | §R2 |
| Spring AOP decorator for logging and metrics | Direct DEBUG logging; no metrics | §R9, FR-028 |
| `ErrorResponse(code, message)` body | RFC 9457 `ProblemDetail` | FR-036 |
| `management.endpoint.switchboard.enabled` | `management.endpoint.switchboard.access` | §R10 |
| `com.yourorg` | `com.llizzard` | Constitution v1.1.1 |

Kept from the draft: the strategy, registry, and chain-of-responsibility patterns; the rules
engine set aside; `Map.copyOf` with a merge function that throws on duplicates; the scoped
unchecked cast; and the module layout.

## R15 — Room for side-by-side versions later (FR-031)

- **Decision**: No versioning in v1. The design leaves room for it: a future resolver can derive
  a versioned key such as `STANDARD_V2` from a version header or from Spring Framework 7's API
  versioning support, without any change to the dispatcher or registry.
- **Rationale**: Keys are opaque strings and resolvers are open-ended, so versioning becomes one
  more resolver.

## R16 — Security context access

- **Decision**: The library has no dependency on Spring Security. Resolvers that need the
  caller's identity read it from the consumer's own security setup, for example
  `SecurityContextHolder` or the request's `getUserPrincipal()`.
- **Rationale**: This meets the spec assumption and FR-009 without forcing a security framework
  onto consumers. `HttpServletRequest.getUserPrincipal()` is available to every resolver through
  the servlet API alone.
