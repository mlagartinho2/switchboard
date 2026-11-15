---

description: "Task list for Switchboard Use-Case Dispatch Library"
---

# Tasks: Switchboard Use-Case Dispatch Library

**Input**: Design documents from `specs/001-use-case-dispatch/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Required. Constitution Principle VI (TDD) is non-negotiable. Every test task comes
before the implementation task it covers. It must be run and seen to **fail for the expected
reason** before the implementation is written.

**Organization**: Tasks are grouped by user story so that each story can be implemented, tested,
and demonstrated on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: The user story the task belongs to (US1–US5)

## Path Conventions

| Alias in this file | Real path |
|---|---|
| `AC/` | `switchboard-autoconfigure/src/main/java/com/llizzard/switchboard/` |
| `AT/` | `switchboard-autoconfigure/src/test/java/com/llizzard/switchboard/` |
| `AR/` | `switchboard-autoconfigure/src/main/resources/` |
| `SM/` | `switchboard-sample/src/main/java/com/llizzard/switchboard/sample/` |
| `SR/` | `switchboard-sample/src/main/resources/` |
| `ST/` | `switchboard-sample/src/test/java/com/llizzard/switchboard/sample/` |

## Rules that apply to every task

- **Effective Java (Principle V)**:
  - Every constructor `Objects.requireNonNull`s its required collaborators with a message.
  - Every stored `List` or `Map` is stored as `List.copyOf` / `Map.copyOf`.
  - `Optional` is used only for single values; methods that return collections return empty
    collections.
- **Dependencies (Principle VII)**: only the JDK, Spring Boot 4.x platform modules, and
  BOM-managed test libraries. Adding anything else requires stopping to get research and
  approval.
- **Bean overrides (Principle II)**: every `@Bean` the library declares carries
  `@ConditionalOnMissingBean`.
- **Class design**: framework service classes (`SwitchboardRegistry`,
  `SwitchboardResolverChain`, `SwitchboardDispatcher`, `SwitchboardStartupValidator`,
  `SwitchboardExceptionHandler`, `SwitchboardEndpoint`) are public and non-final, with public
  constructors, so consumers can replace them (FR-029).
- **No payload contents** may appear in log lines, exception messages, or problem `detail`
  (FR-027, FR-036).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Maven multi-module skeleton per [plan.md](plan.md) and [research.md](research.md)
§R1 and §R13.

- [X] T001 Create the root `pom.xml` (`switchboard-parent`):
  - Coordinates: `groupId com.llizzard`, `artifactId switchboard-parent`, `version 2.0.0`,
    `packaging pom`.
  - Modules: `switchboard-autoconfigure`, `switchboard-spring-boot-starter`,
    `switchboard-sample`.
  - Import `org.springframework.boot:spring-boot-dependencies` in `dependencyManagement`, pinned
    to the latest **GA** Spring Boot 4.x version on Maven Central (no milestone or RC versions,
    Principle VII). Use the BOM import, not `spring-boot-starter-parent`.
  - Set `maven.compiler.release=17` and `project.build.sourceEncoding=UTF-8`.
  - Configure `maven-surefire-plugin` to run JUnit Jupiter.
  - Add `distributionManagement` repository `internal-releases`, with its URL taken from property
    `switchboard.nexus.url`. Leave a `TODO(NEXUS_URL)` comment, because the real URL is not yet
    known (§R13).
- [X] T002 [P] Create `switchboard-autoconfigure/pom.xml`:
  - Parent: `switchboard-parent`.
  - Dependencies:
    - `spring-boot-autoconfigure`
    - `spring-webmvc`
    - `jakarta.servlet:jakarta.servlet-api` (`provided`)
    - Boot's actuator autoconfigure module (`<optional>true</optional>`)
    - `spring-boot-configuration-processor` (`<optional>true</optional>`, also registered as an
      annotation processor path)
  - Test dependencies: `spring-boot-starter-test` and the Boot 4 WebMVC test starter.
  - Confirm the exact Boot 4 module artifact IDs against the pinned BOM (§R1, "verify at
    implementation").
- [X] T003 [P] Create `switchboard-spring-boot-starter/pom.xml`:
  - Parent: `switchboard-parent`.
  - Dependencies, with no `src/` directory: `com.llizzard:switchboard-autoconfigure:${project.version}`
    and `org.springframework.boot:spring-boot-starter-webmvc`.
- [X] T004 [P] Create `switchboard-sample/pom.xml`:
  - Parent: `switchboard-parent`.
  - Dependencies: `switchboard-spring-boot-starter`, `spring-boot-starter-actuator`, and
    `spring-boot-starter-test` (test scope).
  - Add `spring-boot-maven-plugin`.
  - Set property `maven.deploy.skip=true` so the sample is never published (Principle I).
- [X] T005 Create the empty `AR/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  file and the package directories `AC/`, `AC/autoconfigure/`, `AC/web/`, `AC/actuate/`, `AT/`,
  `SM/`, and `ST/`. Run `mvn -q verify` from the repo root and confirm that all four modules
  build with zero tests.

**Checkpoint**: an empty multi-module build passes.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Public value types, contracts, exceptions, and configuration binding, which every
story needs. Signatures come from [contracts/java-api.md](contracts/java-api.md) and
[contracts/configuration.md](contracts/configuration.md).

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

### Tests (write first, confirm they fail)

- [ ] T006 [P] Write `AT/SwitchboardGroupTest.java` for `SwitchboardGroup.of(name, requestType,
  responseType)`:
  - A name that does not match `[a-z0-9]+(-[a-z0-9]+)*`, such as `"Refunds"`, `"refunds_x"`,
    `""`, or `"-a"`, throws `IllegalArgumentException` naming the bad value (rule V9).
  - A null `name`, `requestType`, or `responseType` throws `NullPointerException` with a
    message.
  - `equals` and `hashCode` cover the name and both types. Two handles with the same name and
    different types are **not** equal.
  - `toString()` returns the name.
- [ ] T007 [P] Write `AT/SwitchboardDispatchExceptionTest.java`:
  - `SwitchboardUnresolvedException("refunds").group()` returns `"refunds"`.
  - `SwitchboardNotAvailableException("refunds","X")` exposes `group()` and `caseKey()`.
  - Both messages name the group (and the key, where there is one).
  - Both are subtypes of the sealed `SwitchboardDispatchException`, and of `RuntimeException`.
- [ ] T008 [P] Write `AT/autoconfigure/SwitchboardPropertiesBindingTest.java` using
  `ApplicationContextRunner` with `@EnableConfigurationProperties(SwitchboardProperties.class)`.
  It asserts that:
  - `switchboard.enabled` defaults to `true`.
  - `switchboard.groups.refunds.resolution-order` that is absent binds to `null`, while
    `resolution-order[0]=a` binds to `[a]`.
  - The bracketed key `switchboard.groups.refunds.cases.[REFUND_STANDARD].enabled=false` binds
    with key `REFUND_STANDARD`, keeping case and underscore (research §R5).
  - `CaseSettings.enabled` defaults to `true`, and `owner`, `version`, and `description` default
    to `null`.
  - The bound `groups` and `cases` maps and the `resolutionOrder` list are unmodifiable.

### Implementation

- [ ] T009 [P] Implement `AC/SwitchboardGroup.java`: a final class with private constructor, the
  static factory `of`, validation exactly as in T006, accessors `name()`, `requestType()`,
  `responseType()`, and `equals`/`hashCode`/`toString`. Make T006 pass.
- [ ] T010 [P] Create the interface `AC/SwitchboardStrategy.java` with methods
  `SwitchboardGroup<Req, Res> group()`, `String caseKey()`, and
  `Res execute(Req request, SwitchboardContext context)`. Javadoc must state that the key must
  match `[A-Za-z0-9_-]+` and be unique within the group.
- [ ] T011 [P] Create the interface `AC/SwitchboardResolver.java` with methods
  `SwitchboardGroup<Req, ?> group()`, `String name()`, and
  `Optional<String> resolve(jakarta.servlet.http.HttpServletRequest httpRequest, Req payload)`.
  Javadoc must state that an empty or blank result means "no key, try the next resolver", that
  exceptions propagate unchanged, and that a resolver belongs to exactly one group.
- [ ] T012 [P] Create the records `AC/SwitchboardContext.java` (`group`, `caseKey`,
  `resolverName`) and `AC/SwitchboardResolution.java` (`caseKey`, `resolverName`). Both compact
  constructors call `requireNonNull` on every component.
- [ ] T013 [P] Implement the exceptions so that T007 passes:
  - `AC/SwitchboardDispatchException.java`: `public abstract sealed class ... extends
    RuntimeException permits SwitchboardUnresolvedException, SwitchboardNotAvailableException`,
    with field `group`.
  - `AC/SwitchboardUnresolvedException.java` (final). Message: `"No resolver in group '<g>'
    identified a use case for this request."`
  - `AC/SwitchboardNotAvailableException.java` (final), with `caseKey()`. Message: `"Use case
    '<k>' in group '<g>' is not registered or is disabled."`
- [ ] T014 [P] Implement `AC/autoconfigure/SwitchboardProperties.java`:
  - `@ConfigurationProperties("switchboard")` record with `@DefaultValue("true") boolean
    enabled` and `Map<String, GroupSettings> groups`.
  - Nested record `GroupSettings(List<String> resolutionOrder, Map<String, CaseSettings> cases)`.
  - Nested record `CaseSettings(@DefaultValue("true") boolean enabled, String owner, String
    version, String description)`.
  - Compact constructors take defensive copies: `Map.copyOf`, a null map becomes an empty map,
    and `resolutionOrder` stays `null` when absent but becomes `List.copyOf` when present.
  - Helper methods: `GroupSettings group(String name)` (returns an empty settings object for an
    unknown group, never null) and `boolean isEnabled(String group, String key)` (defaults to
    `true`).
  - Make T008 pass.

**Checkpoint**: `mvn -pl switchboard-autoconfigure verify` is green; the foundation is ready.

---

## Phase 3: User Story 1 - Requests reach the right use case automatically (Priority: P1) 🎯 MVP

**Goal**: A consuming endpoint calls `dispatcher.dispatch(GROUP, http, body)`. The group's
resolvers pick a key in bean order, and the matching strategy runs. Groups are fully isolated.

**Independent Test**: The sample app has a `refunds` group (two strategies, a header resolver
and a payload resolver) and a `payments` group (one strategy). Requests identified through each
resolver reach the right strategy, a key from the other group never runs there, and a strategy
added only in test sources is reachable with no other change (quickstart scenarios 2, 3, 4, 7,
and §5).

### Tests for User Story 1 (write first, confirm they fail)

- [ ] T015 [P] [US1] Write `AT/SwitchboardRegistryTest.java` (plain JUnit, stub strategies). It
  checks:
  - `find("refunds","STANDARD")` returns the stub.
  - `find` for an unknown key, or for a key registered only in another group, returns
    `Optional.empty()`.
  - **Key matching is exact** (spec edge case): with `STANDARD` registered,
    `find("refunds","standard")` and `find("refunds"," STANDARD")` both return
    `Optional.empty()`.
  - `registered("refunds")` returns the key-to-strategy map, and an empty map for an unknown
    group.
  - `groups()` returns the group names.
  - **V1**: two strategies with key `X` in group `refunds` throw `IllegalStateException` whose
    message contains `refunds`, `X`, and both class names.
  - The same key in two **different** groups is allowed.
  - **V2**: a key `"BAD KEY"` (not matching `[A-Za-z0-9_-]+`) throws, naming the key.
  - **V8**: two strategies whose `group()` handles share the name `refunds` but declare
    different request types throw, naming the group.
  - A later mutation of the input list does not affect the registry.
  - A null constructor argument throws `NullPointerException` with a message.
- [ ] T016 [P] [US1] Write `AT/SwitchboardResolverChainTest.java` (plain JUnit,
  `MockHttpServletRequest`, stub resolvers). It checks:
  - With no configured order, resolvers are consulted in the given list order and the first
    non-empty result wins, returning `SwitchboardResolution(key, resolverName)`.
  - Later resolvers are **not** invoked once one answers (verify with a counter).
  - A resolver returning `Optional.of("  ")` or `Optional.of("")` counts as no key, and the
    chain continues.
  - All resolvers empty gives `Optional.empty()`.
  - Only resolvers of the requested group are consulted.
  - An exception thrown by a resolver propagates unchanged.
  - **V3**: two resolvers named `h` in group `refunds` throw, naming the group and both
    resolvers. The same name in two different groups is allowed.
  - **V8**: two resolvers whose group handles share a name but differ in types throw.
  - `resolutionOrder("refunds")` returns the effective name list, and an empty list for an
    unknown group.
- [ ] T017 [P] [US1] Write `AT/SwitchboardDispatcherTest.java` (plain JUnit with Mockito mocks
  of registry and chain, or real instances with stubs). It checks:
  - The happy path returns the strategy's response, and the strategy receives
    `SwitchboardContext("refunds", key, resolverName)` (FR-005).
  - An empty chain result throws `SwitchboardUnresolvedException` with group `refunds`.
  - A resolved key with no strategy throws `SwitchboardNotAvailableException` with the group and
    key.
  - **Group without implementations** (spec edge case): for a group that has resolvers but no
    strategies at all, a resolver that returns a key gives `SwitchboardNotAvailableException`
    (404), and resolvers that all return empty give `SwitchboardUnresolvedException` (501).
  - A strategy exception propagates as the same instance.
  - A null `group` or `httpRequest` throws `NullPointerException`, while a `null` request
    payload is allowed.
  - A null constructor argument throws.
  - Using Spring Boot's `OutputCaptureExtension` with the logger at DEBUG, each outcome
    (`handled`, `not_found`, `not_implemented`, `failed`) produces exactly one line containing
    the group, the key (or `-`), the resolver (or `-`), the outcome, and an elapsed time.
  - No log line contains a marker string placed in the payload's `toString()` (FR-027).
- [ ] T018 [P] [US1] Write `AT/autoconfigure/SwitchboardStartupValidatorTest.java`. This is a
  plain JUnit test with no Spring context, as the constitution's Testing section requires. It
  builds `new SwitchboardStartupValidator(strategies, resolvers, properties)` from stub
  strategies and resolvers and calls `afterSingletonsInstantiated()` directly. It checks:
  - **V6**: a group with a strategy but no resolver throws `IllegalStateException` whose message
    names the group.
  - **V8 across components**: a strategy and a resolver using the same group name with different
    request types throw, naming the group.
  - A valid setup (each group has at least one strategy and one resolver, with matching types)
    does not throw.
  - **Group without implementations** (spec edge case): a group with a resolver but no
    strategies does not throw.
  - A null constructor argument throws `NullPointerException` with a message.
  - A later mutation of the input lists does not change the outcome.
- [ ] T019 [P] [US1] Write `AT/autoconfigure/SwitchboardStartupValidationTest.java` using
  `WebApplicationContextRunner` with `AutoConfigurations.of(SwitchboardAutoConfiguration.class)`
  and user beans registered per case. It checks:
  - **V6**: a group with a strategy but no resolver fails startup, and the root cause message
    names the group.
  - **V8 across components**: a strategy and a resolver using the same group name with
    different types fail startup.
  - A valid setup starts.
  - **Group without implementations** (spec edge case): a context whose only bean for group
    `orphans` is a resolver (no strategy) starts successfully.
- [ ] T020 [P] [US1] Write `AT/autoconfigure/SwitchboardAutoConfigurationTest.java` (activation
  only for now). In a servlet web context with one stub strategy and one stub resolver, it
  asserts that the context has single beans of `SwitchboardRegistry`,
  `SwitchboardResolverChain`, `SwitchboardDispatcher`, `SwitchboardStartupValidator`, and
  `SwitchboardProperties`, and that a non-web context gets none of them.
- [ ] T021 [P] [US1] Write `ST/SampleDispatchTest.java` (`@SpringBootTest` +
  `@AutoConfigureMockMvc`). It checks:
  - `POST /api/refunds` with header `X-Refund-Type: STANDARD` and body `{"amount":50}` returns
    200 with a body showing the `STANDARD` strategy handled it.
  - No header with `{"amount":5000}` is handled by `EXPEDITED`.
  - Header `STANDARD` plus `{"amount":5000}` is handled by `STANDARD` (the first resolver wins).
  - The `EXPEDITED` response populates `expectedSettlementHours` and the `STANDARD` response
    omits it, and both are 200 (US1 scenario 6).
  - `POST /api/refunds` with `X-Refund-Type: CARD` returns 404 and `CardPaymentStrategy` is
    never invoked (assert with `@MockitoSpyBean` or a call counter, SC-010).
  - `POST /api/payments` with `X-Payment-Method: CARD` returns 200 from `CardPaymentStrategy`.
  - **Identity-based resolver** (US1 scenario 4, FR-009): a nested `@SpringBootTest` class
    `@Import`s a test-only `CallerIdentityResolver` defined in the same file. It is a
    `SwitchboardResolver<RefundRequest>` with `@Order(0)`, `group()` = `RefundGroup.REFUNDS`,
    and `name()` = `caller-identity`. It returns `EXPEDITED` when
    `httpRequest.getUserPrincipal()` is non-null and its name is `vip-client`, and empty
    otherwise. The test checks that:
    - A request with no header, `{"amount":50}`, and MockMvc `.principal(() -> "vip-client")`
      is handled by `EXPEDITED`.
    - The same request with `.principal(() -> "regular-client")` falls through the remaining
      resolvers and returns 501.
    - The same request with no principal returns 501.
    - No Spring Security dependency is added (research §R16).
- [ ] T022 [P] [US1] Write `ST/SampleNewUseCaseTest.java` (SC-001). `@SpringBootTest` that
  `@Import`s a test-only `PartialRefundStrategy` (key `PARTIAL`, group `RefundGroup.REFUNDS`)
  defined in the same test file. `X-Refund-Type: PARTIAL` returns 200 from it, without any
  change to main sources or `application.yml`.
- [ ] T023 [P] [US1] Write `ST/refunds/RefundResolversTest.java` (plain JUnit,
  `MockHttpServletRequest`, no Spring context; required by the constitution's Testing section,
  "every SwitchboardResolver has unit tests"). It checks:
  - `RefundTypeHeaderResolver`: `name()` is `refund-type-header`, `group()` is
    `RefundGroup.REFUNDS`, it returns the `X-Refund-Type` header value, and it returns empty when
    the header is absent.
  - `RefundAmountResolver`: `name()` is `refund-amount`, it returns `EXPEDITED` for
    `amount = 1000.01` and for `amount = 5000`, and it returns empty for `amount = 1000`,
    `amount = 50`, and a `null` payload.
  - The test must fail to compile or fail until T032 exists.
- [ ] T024 [P] [US1] Write `ST/payments/PaymentMethodHeaderResolverTest.java` (plain JUnit,
  `MockHttpServletRequest`). It checks that `name()` is `payment-method-header`, `group()` is
  `PaymentGroup.PAYMENTS`, it returns the `X-Payment-Method` header value, and it returns empty
  when the header is absent. The test must fail until T033 exists.

### Implementation for User Story 1

- [ ] T025 [US1] Implement `AC/SwitchboardRegistry.java`:
  - Constructor `(List<SwitchboardStrategy<?, ?>> strategies, SwitchboardProperties properties)`.
  - `requireNonNull` on both arguments.
  - Group the strategies into an immutable `Map<String, Map<String, SwitchboardStrategy<?,?>>>`
    using `Collectors.toMap` with a merge function that throws the **V1** message. Wrap each
    level in `Map.copyOf`.
  - Validate **V2** (`[A-Za-z0-9_-]+`) and **V8**.
  - Methods: `find(group, caseKey)` returning `Optional`, `registered(group)` returning an
    empty map when the group is unknown, and `groups()`.
  - The properties are stored for US3, and enablement is not applied yet.
  - Make T015 pass.
- [ ] T026 [US1] Implement `AC/SwitchboardResolverChain.java`:
  - Constructor `(List<SwitchboardResolver<?>> orderedResolvers, SwitchboardProperties
    properties)`. Take a defensive copy and group the resolvers per group name, preserving the
    given order.
  - Validate **V3** and **V8**.
  - `<Req> Optional<SwitchboardResolution> resolve(SwitchboardGroup<Req, ?> group,
    HttpServletRequest httpRequest, Req payload)`: walk the group's resolvers, skip
    empty or blank results, and return the first match.
  - Expose `resolutionOrder(group)` and `groups()`.
  - The cast from `SwitchboardResolver<?>` to `SwitchboardResolver<Req>` is a scoped local
    `@SuppressWarnings("unchecked")` with a one-line safety comment that refers to V8.
  - Make T016 pass.
- [ ] T027 [US1] Implement `AC/SwitchboardDispatcher.java` exactly per
  [contracts/java-api.md](contracts/java-api.md):
  - Constructor `(SwitchboardResolverChain, SwitchboardRegistry)` with `requireNonNull`.
  - `dispatch` follows the flow in [data-model.md](data-model.md) "Dispatch flow".
  - Include the dispatcher's scoped `@SuppressWarnings("unchecked")` local cast (cast 1 in the
    contract), with its comment (Principle V, Item 27).
  - Add SLF4J DEBUG logging with outcomes `handled`, `not_found`, `not_implemented`, and
    `failed` (rethrow after logging), and elapsed time via `System.nanoTime()` in microseconds.
    Never log the payload (§R9).
  - Make T017 pass.
- [ ] T028 [US1] Implement `AC/autoconfigure/SwitchboardStartupValidator.java`:
  - Implements `SmartInitializingSingleton`.
  - Constructor `(List<SwitchboardStrategy<?,?>>, List<SwitchboardResolver<?>>,
    SwitchboardProperties)` with `requireNonNull` and defensive copies.
  - In `afterSingletonsInstantiated()`, throw `IllegalStateException` for **V6** ("Group 'g'
    has strategies but no resolvers") and for **V8 across strategies and resolvers**.
  - Make T018 and the T019 cases pass. Its US3 rules are added in T047.
- [ ] T029 [US1] Implement `AC/autoconfigure/SwitchboardAutoConfiguration.java`:
  - Annotations: `@AutoConfiguration`, `@ConditionalOnWebApplication(type = SERVLET)`,
    `@ConditionalOnProperty(prefix = "switchboard", name = "enabled", havingValue = "true",
    matchIfMissing = true)`, and `@EnableConfigurationProperties(SwitchboardProperties.class)`.
  - `@Bean @ConditionalOnMissingBean` methods for `SwitchboardRegistry` (strategies from
    `ObjectProvider<SwitchboardStrategy<?, ?>>.orderedStream().toList()`),
    `SwitchboardResolverChain` (resolvers from `ObjectProvider<SwitchboardResolver<?>>
    .orderedStream().toList()`, which gives Spring bean order per §R6), `SwitchboardDispatcher`,
    and `SwitchboardStartupValidator`.
  - Register the class in
    `AR/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
  - Make T019 and T020 pass.
- [ ] T030 [P] [US1] Create the sample refunds contract:
  - `SM/refunds/RefundRequest.java`: record `(String orderId, BigDecimal amount)`.
  - `SM/refunds/RefundResponse.java`: record `(String handledBy, String status, Integer
    expectedSettlementHours)`. `expectedSettlementHours` is nullable, filled only by
    `EXPEDITED`, and annotated `@JsonInclude(NON_NULL)`.
  - `SM/refunds/RefundGroup.java`: constant `REFUNDS = SwitchboardGroup.of("refunds",
    RefundRequest.class, RefundResponse.class)`.
- [ ] T031 [P] [US1] Create the sample payments contract:
  - `SM/payments/PaymentRequest.java`: record `(String orderId, BigDecimal amount)`.
  - `SM/payments/PaymentResponse.java`: record `(String handledBy, String status)`.
  - `SM/payments/PaymentGroup.java`: constant `PAYMENTS = SwitchboardGroup.of("payments",
    PaymentRequest.class, PaymentResponse.class)`.
- [ ] T032 [US1] Create the sample refunds strategies and resolvers, all `@Component`:
  - `SM/refunds/StandardRefundStrategy.java`: key `STANDARD`.
  - `SM/refunds/ExpeditedRefundStrategy.java`: key `EXPEDITED`; sets
    `expectedSettlementHours=4`.
  - `SM/refunds/RefundTypeHeaderResolver.java`: `@Order(1)`, name `refund-type-header`; returns
    the `X-Refund-Type` header value.
  - `SM/refunds/RefundAmountResolver.java`: `@Order(2)`, name `refund-amount`; returns
    `EXPEDITED` when `payload.amount() > 1000`, otherwise empty.
  - Depends on T030.
  - Make T023 pass.
- [ ] T033 [US1] Create the sample payments strategy and resolver, both `@Component`:
  - `SM/payments/CardPaymentStrategy.java`: key `CARD`.
  - `SM/payments/PaymentMethodHeaderResolver.java`: name `payment-method-header`; returns the
    `X-Payment-Method` header value.
  - Depends on T031.
  - Make T024 pass.
- [ ] T034 [US1] Create the sample controllers and application:
  - `SM/refunds/RefundController.java`: `POST /api/refunds` returns
    `dispatcher.dispatch(RefundGroup.REFUNDS, http, body)`.
  - `SM/payments/PaymentController.java`: `POST /api/payments`, written the same way.
  - Neither controller has any per-use-case branching.
  - `SM/SampleApplication.java`: `@SpringBootApplication`.
  - `SR/application.yml`: an empty `switchboard:` block for now.
  - Make T021 and T022 pass.

**Checkpoint**: the MVP works. Dispatch, group isolation, and adding a use case all work end to
end in the sample.

---

## Phase 4: User Story 2 - Callers can tell "unknown request" from "unavailable use case" (Priority: P2)

**Goal**: Both failure exceptions become RFC 9457 problem-details responses (501 and 404) with
stable error codes, without any consumer code.

**Independent Test**: In the sample, a request with no matching resolver returns 501 with
`USE_CASE_UNRESOLVED`, and an unknown key returns 404 with `USE_CASE_NOT_AVAILABLE` and the key,
even when the consumer has its own catch-all exception handler (quickstart scenarios 5 and 6).

### Tests for User Story 2 (write first, confirm they fail)

- [ ] T035 [P] [US2] Write `AT/web/SwitchboardExceptionHandlerTest.java` (plain JUnit, calling
  the handler methods directly with a `MockHttpServletRequest` whose URI is `/api/refunds`). It
  asserts every field in [contracts/http-errors.md](contracts/http-errors.md):
  - **Unresolved**: status 501; `type` = `urn:switchboard:error:use-case-unresolved`; `title`
    "Use case not resolved"; `instance` `/api/refunds`; property `errorCode` =
    `USE_CASE_UNRESOLVED`; property `group` = `refunds`; no `key` property.
  - **Not available**: status 404; `type` = `urn:switchboard:error:use-case-not-available`;
    `title` "Use case not available"; `errorCode` = `USE_CASE_NOT_AVAILABLE`; `group` and `key`
    properties set.
  - Neither `detail` contains payload data.
- [ ] T036 [P] [US2] Write `ST/SampleFailureResponsesTest.java` (MockMvc). It checks:
  - `POST /api/refunds` with no header and `{"amount":50}` returns 501, `Content-Type
    application/problem+json`, JSON `errorCode` `USE_CASE_UNRESOLVED`, and `group` `refunds`.
  - Header `X-Refund-Type: NOPE` returns 404 with `errorCode` `USE_CASE_NOT_AVAILABLE` and
    `key` `NOPE`.
  - With a test-only `@RestControllerAdvice` that handles `Exception.class` (returns 500)
    `@Import`ed, both requests still return 501 and 404, proving the library handler wins
    (§R8).
  - A strategy that throws `IllegalStateException` (test-only, key `BOOM`) is **not** converted
    to 501 or 404 (spec edge case "Business-logic failures").
  - **Failing resolver** (spec edge case "Failing custom signal"): a test-only `@Order(0)`
    refunds resolver named `explosive` throws `IllegalStateException` when the request carries
    header `X-Explode: true`, and returns empty otherwise. A request with that header gets 500
    from the test-only catch-all advice, never 501 or 404. A request without the header is still
    dispatched normally.
- [ ] T037 [P] [US2] Extend `AT/autoconfigure/SwitchboardAutoConfigurationTest.java`: in the
  activated servlet context from T020, assert there is exactly one `SwitchboardExceptionHandler`
  bean. It must fail until T038.

### Implementation for User Story 2

- [ ] T038 [US2] Implement and register `AC/web/SwitchboardExceptionHandler.java`:
  - `@RestControllerAdvice` with `@Order(Ordered.HIGHEST_PRECEDENCE)`.
  - Two `@ExceptionHandler` methods, for `SwitchboardUnresolvedException` and
    `SwitchboardNotAvailableException`. Each returns `ResponseEntity<ProblemDetail>`, built with
    `ProblemDetail.forStatusAndDetail`, then `setType(URI.create(...))`, `setTitle`,
    `setInstance(URI.create(request.getRequestURI()))`, and `setProperty("errorCode"|"group"|
    "key", ...)`.
  - Hold the error codes and type URIs as `public static final` constants on the class,
    because they are a public contract.
  - Register it as `@Bean @ConditionalOnMissingBean` in
    `AC/autoconfigure/SwitchboardAutoConfiguration.java`.
  - Make T035, T036, and T037 pass.

**Checkpoint**: US1 and US2 both work. Callers get distinct, standard failure responses.

---

## Phase 5: User Story 3 - Operators change behavior through configuration only (Priority: P3)

**Goal**: A per-group kill switch, a configured resolver order, the whole-library switch, and
startup failure on bad configuration.

**Independent Test**: In the sample, setting `EXPEDITED` to disabled makes requests for it
return 404, while it is still reported as registered. Reversing or trimming `resolution-order`
changes which resolver wins. An unknown resolver name stops startup. `switchboard.enabled=false`
starts with no Switchboard beans (quickstart §4).

### Tests for User Story 3 (write first, confirm they fail)

- [ ] T039 [P] [US3] Extend `AT/SwitchboardRegistryTest.java`:
  - With properties `refunds.cases.[EXPEDITED].enabled=false` (built directly as
    `SwitchboardProperties`), `find("refunds","EXPEDITED")` is empty, but
    `registered("refunds")` still contains `EXPEDITED` and `isEnabled("refunds","EXPEDITED")`
    is `false` (FR-014, FR-015).
  - `isEnabled` defaults to `true` with no setting (FR-016).
  - Disabling `STANDARD` in `refunds` leaves `STANDARD` in `payments` enabled (FR-034).
- [ ] T040 [P] [US3] Extend `AT/SwitchboardResolverChainTest.java`:
  - A configured `resolution-order: [b, a]` consults `b` before `a`, regardless of list order.
  - A configured `[a]` never consults `b`.
  - **V4**: an order naming `zzz` throws, naming the group and `zzz`.
  - **V5**: an explicit empty order (`List.of()`) throws, naming the group.
  - An absent order (`null`) falls back to the given order.
  - `resolutionOrder(group)` returns the configured order when present.
- [ ] T041 [P] [US3] Extend `AT/autoconfigure/SwitchboardStartupValidationTest.java`:
  - **V7**: `switchboard.groups.ghost.cases.[X].enabled=true` with no strategy or resolver in
    `ghost` fails startup, naming `ghost`.
  - **W1**: `switchboard.groups.refunds.cases.[REFUNDSTANDARD].owner=x` with no matching
    strategy starts successfully and logs a WARN naming the group and the key (use
    `OutputCaptureExtension`).
  - **V4** through the full context: an unknown name in `resolution-order` fails startup.
- [ ] T042 [P] [US3] Extend `AT/autoconfigure/SwitchboardStartupValidatorTest.java` (plain
  JUnit, no Spring context). It checks:
  - **V7**: properties that configure group `ghost` (with a case entry) while no strategy or
    resolver uses `ghost` make `afterSingletonsInstantiated()` throw, naming `ghost`.
  - **W1**: properties that configure case `REFUNDSTANDARD` in group `refunds`, which has no
    such strategy, do not throw. Instead they log one WARN naming the group and the key and
    mentioning the bracket notation (use `OutputCaptureExtension`).
- [ ] T043 [P] [US3] Extend `AT/autoconfigure/SwitchboardAutoConfigurationTest.java`: with
  `switchboard.enabled=false`, the context starts and contains **no** `SwitchboardRegistry`,
  `SwitchboardResolverChain`, `SwitchboardDispatcher`, `SwitchboardStartupValidator`, or
  `SwitchboardExceptionHandler` (FR-017).
- [ ] T044 [P] [US3] Write `ST/SampleConfigurationTest.java`, using nested
  `@SpringBootTest(properties = ...)` classes:
  - (a) `switchboard.groups.refunds.cases.[EXPEDITED].enabled=false`: no header with
    `{"amount":5000}` returns 404, header `STANDARD` returns 200, and `POST /api/payments` with
    `CARD` returns 200 (SC-003).
  - (b) `switchboard.groups.refunds.resolution-order=refund-amount,refund-type-header`: header
    `STANDARD` with `{"amount":5000}` is handled by `EXPEDITED`.
  - (c) `resolution-order=refund-type-header`: no header with `{"amount":5000}` returns 501.
  - (d) `switchboard.enabled=false`: the context starts, and `POST /api/refunds` returns 404
    because the controller is absent.
  - (e) `resolution-order=does-not-exist`: use `SpringApplication` (or
    `SpringApplicationBuilder`) to start the sample, and assert it throws with a root cause
    message containing `refunds` and `does-not-exist` (SC-006).

### Implementation for User Story 3

- [ ] T045 [US3] Apply enablement in `AC/SwitchboardRegistry.java`: `find` returns only strategies
  where `properties.isEnabled(group, key)` is true. Add `isEnabled(group, key)`. Make T039 pass.
- [ ] T046 [US3] Apply the configured order in `AC/SwitchboardResolverChain.java`: when
  `properties.group(g).resolutionOrder()` is non-null, build the group's chain from exactly
  those names in that order. Throw **V4** for an unknown name and **V5** for an empty list.
  Make T040 pass.
- [ ] T047 [US3] Extend `AC/autoconfigure/SwitchboardStartupValidator.java`: throw for **V7**
  (a configured group name with no strategies and no resolvers), and log the **W1** WARN via
  SLF4J for each configured case key that has no strategy in its group. The WARN message must
  mention the bracket notation (§R5). Make T041 and T042 pass.
- [ ] T048 [US3] Make the sample controllers back off with the library:
  - Annotate `SM/refunds/RefundController.java` and `SM/payments/PaymentController.java` with
    `@ConditionalOnProperty(prefix = "switchboard", name = "enabled", havingValue = "true",
    matchIfMissing = true)`, following the consumer pattern in §R11.
  - Add the sample `cases` block to `SR/application.yml`, with bracketed keys plus owner,
    version, and description for `refunds` `[STANDARD]` and `[EXPEDITED]`, and `payments`
    `[CARD]`, per [contracts/configuration.md](contracts/configuration.md).
  - Make T043 and T044 pass.

**Checkpoint**: US1–US3 work. Every operator control is configuration-only.

---

## Phase 6: User Story 4 - Operators can see what is registered and how routing is set up (Priority: P4)

**Goal**: A read-only `/actuator/switchboard` report, organized by group, registered only when
actuator is present.

**Independent Test**: The sample's `GET /actuator/switchboard` shows both groups with their
effective order, and shows an enabled case, a disabled-but-registered case, and a
configured-but-unregistered case correctly (quickstart scenario 8).

### Tests for User Story 4 (write first, confirm they fail)

- [ ] T049 [P] [US4] Write `AT/actuate/SwitchboardEndpointTest.java` (plain JUnit, real registry
  and chain built from stubs and `SwitchboardProperties`). It asserts:
  - The report covers every group that has a strategy or resolver, with group keys sorted.
  - `resolutionOrder` is the **effective** order (configured, or else default).
  - `cases` is the union of registered and configured keys, sorted by key.
  - The four rows of the enabled/registered matrix in [data-model.md](data-model.md) are
    correct.
  - `strategyClass` is the user class name: give a CGLIB-style subclass and expect the parent
    name, via `ClassUtils.getUserClass`. It is `null` when nothing is registered.
  - `owner`, `version`, and `description` come from configuration, or are `null`.
- [ ] T050 [P] [US4] Write `AT/actuate/SwitchboardEndpointAutoConfigurationTest.java`
  (`WebApplicationContextRunner` with both auto-configurations plus Boot's endpoint
  auto-configuration). It asserts the endpoint bean is:
  - present by default;
  - absent with `management.endpoint.switchboard.access=none` (confirm the property name on the
    pinned Boot version, §R10);
  - absent when actuator classes are hidden with `FilteredClassLoader(Endpoint.class)`, and that
    context still starts (FR-026);
  - absent with `switchboard.enabled=false`.
- [ ] T051 [P] [US4] Write `ST/SampleEndpointTest.java` (MockMvc, with
  `switchboard.groups.refunds.cases.[EXPEDITED].enabled=false` and
  `switchboard.groups.refunds.cases.[LEGACY].owner=payments-team`). It asserts
  `GET /actuator/switchboard` returns JSON matching
  [contracts/actuator-endpoint.md](contracts/actuator-endpoint.md):
  - `groups.refunds.resolutionOrder` is `["refund-type-header","refund-amount"]`.
  - `EXPEDITED` has enabled false and registered true.
  - `LEGACY` has enabled true, registered false, and strategyClass null.
  - `groups.payments.cases[0].key` is `CARD`.

### Implementation for User Story 4

- [ ] T052 [P] [US4] Create the records in `AC/actuate/SwitchboardReport.java`: `SwitchboardReport(Map<String,
  GroupReport> groups)`, `GroupReport(List<String> resolutionOrder, List<CaseReport> cases)`,
  and `CaseReport(String key, boolean enabled, boolean registered, String strategyClass, String
  owner, String version, String description)`. All collections are defensively copied, and
  `groups` is a sorted (`TreeMap`) copy wrapped as unmodifiable.
- [ ] T053 [US4] Implement `AC/actuate/SwitchboardEndpoint.java`: `@Endpoint(id =
  "switchboard")`, constructor `(SwitchboardRegistry, SwitchboardResolverChain,
  SwitchboardProperties)` with `requireNonNull`, and one `@ReadOperation SwitchboardReport
  report()` built according to the rules in
  [contracts/actuator-endpoint.md](contracts/actuator-endpoint.md). Make T049 pass.
- [ ] T054 [US4] Implement `AC/autoconfigure/SwitchboardEndpointAutoConfiguration.java`:
  - Annotations: `@AutoConfiguration(after = SwitchboardAutoConfiguration.class)`,
    `@ConditionalOnClass(Endpoint.class)`, and
    `@ConditionalOnBean(SwitchboardRegistry.class)`.
  - `@Bean @ConditionalOnMissingBean @ConditionalOnAvailableEndpoint` for `SwitchboardEndpoint`.
  - Append the class to
    `AR/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
  - Make T050 pass.
- [ ] T055 [US4] Expose the endpoint in the sample: add
  `management.endpoints.web.exposure.include: health,switchboard` to `SR/application.yml`.
  Make T051 pass.

**Checkpoint**: US1–US4 work. Operators can inspect routing without reading code or
configuration.

---

## Phase 7: User Story 5 - Consuming teams can replace any framework part without forking (Priority: P5)

**Goal**: Each of the six library beans can be replaced by a consumer bean (FR-029, SC-007).

**Independent Test**: For each bean type, a context with a user-defined bean of that type uses
the user's bean, and contains no second instance.

### Tests for User Story 5 (write first, confirm they fail where a gap exists)

- [ ] T056 [P] [US5] Extend `AT/autoconfigure/SwitchboardAutoConfigurationTest.java` with one
  override test per bean: `SwitchboardRegistry`, `SwitchboardResolverChain`,
  `SwitchboardDispatcher`, `SwitchboardStartupValidator`, and `SwitchboardExceptionHandler`.
  Each test registers a user subclass via `withBean(...)` and asserts
  `context.getBean(Type.class)` is that instance and `getBeansOfType(Type.class)` has size 1.
- [ ] T057 [P] [US5] Extend `AT/actuate/SwitchboardEndpointAutoConfigurationTest.java` with an
  override test for `SwitchboardEndpoint`, written the same way as T056.

### Implementation for User Story 5

- [ ] T058 [US5] Fix any failure from T056 or T057. Confirm that every `@Bean` method in
  `AC/autoconfigure/SwitchboardAutoConfiguration.java` and
  `AC/autoconfigure/SwitchboardEndpointAutoConfiguration.java` carries
  `@ConditionalOnMissingBean`, and that each overridable class is public, non-final, and has a
  public constructor.

**Checkpoint**: all five user stories work independently.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Performance, documentation, constitution compliance, and release readiness.

- [ ] T059 [P] Write `AT/SwitchboardDispatchPerformanceTest.java` (SC-008, §R12):
  - Use a real chain and registry with one group, three stub resolvers (the third one answers),
    and a stub strategy returning a constant.
  - Use one `MockHttpServletRequest`, reused across calls.
  - Run 10,000 warm-up and 100,000 measured `dispatch` calls, timed individually with
    `System.nanoTime()`.
  - Assert p95 < 1,000,000 ns.
  - Do not use JMH (Principle VII).
- [ ] T060 [P] Add Javadoc to every public type and method in `AC/`, covering contracts,
  nullability, thrown exceptions, and the key and group name formats. Check with
  `mvn -pl switchboard-autoconfigure javadoc:javadoc` (the Maven Javadoc plugin is part of the
  standard build tooling; no new library dependency).
- [ ] T061 [P] Write the root `README.md`, covering:
  - The support matrix (Switchboard 2.0.0, Spring Boot 4.x, Java 17+).
  - The starter dependency snippet (`com.llizzard:switchboard-spring-boot-starter:2.0.0`).
  - A consumer walkthrough (group constant, strategy, resolver, one-line controller).
  - The configuration reference, linking
    [contracts/configuration.md](contracts/configuration.md), including **bracket notation for
    case keys** and the `SPRING_APPLICATION_JSON` note.
  - The error contract, linking [contracts/http-errors.md](contracts/http-errors.md).
  - The endpoint exposure and security note.
  - The `switchboard.enabled=false` consumer obligation (§R11).
- [ ] T062 Run a constitution compliance audit and fix any finding:
  - `grep -rn "SuppressWarnings" switchboard-autoconfigure/src/main` shows only the two scoped
    local casts (dispatcher and chain), each with a comment.
  - Every framework constructor calls `requireNonNull`.
  - No stored collection skips `List.copyOf` / `Map.copyOf`.
  - `mvn dependency:tree` for `switchboard-autoconfigure` and the starter lists only Spring,
    Jakarta, Jackson, SLF4J/Logback, Micrometer-observation (if transitively pulled by Spring),
    and BOM-managed test artifacts. Report anything else for approval (Principle VII).
- [ ] T063 Verify packaging:
  - After `mvn verify`, the starter jar contains no `.class` files.
  - The autoconfigure jar contains
    `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` listing
    both auto-configurations, plus the generated
    `META-INF/spring-configuration-metadata.json`.
  - `mvn -pl switchboard-sample help:effective-pom` shows `maven.deploy.skip=true`.
  - **No consumer code in the library** (FR-008, FR-030): a test
    `AT/LibraryContentsTest.java` scans the `com.llizzard.switchboard` main classes, using
    Spring's `ClassPathScanningCandidateComponentProvider` (no new dependency). It asserts that
    no concrete class implements `SwitchboardStrategy` or `SwitchboardResolver`, and that no
    class is annotated `@Controller` or `@RestController`.
- [ ] T064 Run every scenario in [quickstart.md](quickstart.md) against
  `mvn -pl switchboard-sample -am spring-boot:run`, including the restart-based configuration
  changes in §4. Record any mismatch as a bug, starting with a failing test (Principle VI).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup and blocks all user stories.
- **US1 (Phase 3)**: depends on Foundational. It is the MVP and provides the registry, chain,
  dispatcher, auto-configuration, and sample that later stories extend.
- **US2 (Phase 4)**: depends on Foundational. Its unit test (T035) is independent of US1. Its
  sample test (T036) needs the US1 sample, and its auto-configuration test (T037) and the bean
  registration in T038 extend files created in US1 (T020, T029).
- **US3 (Phase 5)**: extends the US1 registry, chain, and validator classes, so it depends on
  US1.
- **US4 (Phase 6)**: needs the US1 registry and chain. It reads enablement from US3 to report
  disabled cases accurately, so do it after US3.
- **US5 (Phase 7)**: needs every bean to exist, so it depends on US1, US2, and US4.
- **Polish (Phase 8)**: depends on all desired stories.

### Within Each Story

- Test tasks first. Run them and confirm they fail, then implement until they pass (Principle
  VI).
- Value types before services, services before auto-configuration, and auto-configuration
  before sample wiring.
- Tasks that edit the same file are sequential: T015 then T039; T016 then T040; T018 then T042;
  T020 then T037, T043, and T056; T029 then T038; T025 then T045; T026 then T046; T028 then
  T047.

### Parallel Opportunities

- Setup: T002, T003, and T004 can run together after T001.
- Foundational: tests T006–T008 together, then implementations T009–T014 together (all
  different files).
- US1: tests T015–T024 together. Then T025, T026, T030, and T031 can run in parallel, while
  T027 waits for T025 and T026, and T029 waits for T025–T028.
- US2: tests T035, T036, and T037 together. The handler class itself (first part of T038) can
  be written right after T035, in parallel with US1. Its registration waits for T029.
- US3: tests T039–T044 together.
- US4: tests T049–T051 together, and T052 in parallel with the tests.
- Polish: T059, T060, and T061 together.

---

## Parallel Example: User Story 1

```bash
# All US1 tests at once (each must fail first):
Task: "T015 SwitchboardRegistryTest in AT/SwitchboardRegistryTest.java"
Task: "T016 SwitchboardResolverChainTest in AT/SwitchboardResolverChainTest.java"
Task: "T017 SwitchboardDispatcherTest in AT/SwitchboardDispatcherTest.java"
Task: "T018 SwitchboardStartupValidatorTest in AT/autoconfigure/ (plain JUnit)"
Task: "T019 SwitchboardStartupValidationTest in AT/autoconfigure/"
Task: "T020 SwitchboardAutoConfigurationTest in AT/autoconfigure/"
Task: "T021 SampleDispatchTest in ST/SampleDispatchTest.java"
Task: "T022 SampleNewUseCaseTest in ST/SampleNewUseCaseTest.java"
Task: "T023 RefundResolversTest in ST/refunds/RefundResolversTest.java"
Task: "T024 PaymentMethodHeaderResolverTest in ST/payments/PaymentMethodHeaderResolverTest.java"

# Then the independent implementation files:
Task: "T025 SwitchboardRegistry in AC/SwitchboardRegistry.java"
Task: "T026 SwitchboardResolverChain in AC/SwitchboardResolverChain.java"
Task: "T030 Sample refunds contract in SM/refunds/"
Task: "T031 Sample payments contract in SM/payments/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup, then Phase 2 Foundational.
2. Phase 3 (US1): dispatch works end to end in the sample with group isolation.
3. **Stop and validate** with quickstart scenarios 2, 3, 4, and 7 and §5. Failures surface as
   raw exceptions (500) until US2 lands.

### Incremental Delivery

1. Setup and Foundational give the foundation.
2. US1: the dispatch MVP.
3. US2: standard 501/404 responses, which make the library safe for real callers.
4. US3: the kill switch, configured order, and startup validation, which make it safe to run in
   production.
5. US4: the operator report.
6. US5: override guarantees, then Polish, then the 2.0.0 release.

### Release Gate

Do not tag 2.0.0 until:
- T062 (constitution audit) and T063 (packaging) pass;
- `TODO(NEXUS_URL)` in the root `pom.xml` is resolved;
- the error codes and type URIs in `SwitchboardExceptionHandler` have been reviewed one final
  time, because they cannot change after release.

---

## Notes

- [P] tasks use different files and have no dependency on incomplete tasks.
- Commit after each green test-and-implementation pair.
- A task that seems to need a new third-party library must stop and ask for approval (Principle
  VII). Do not add the dependency first.
