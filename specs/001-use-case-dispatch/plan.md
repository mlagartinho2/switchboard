# Implementation Plan: Switchboard Use-Case Dispatch Library

**Branch**: `001-use-case-dispatch` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-use-case-dispatch/spec.md`, plus the earlier
draft in `copilot/input/plan.md` (that draft predates the spec's clarifications; see
[research.md](research.md) §R14 for what was superseded).

## Summary

Switchboard is a Spring Boot 4.x library. A consuming application registers several
implementations (strategies) of one request/response contract, and the library picks the right
one for each HTTP request. Use cases are organized into **dispatch groups**, one per contract. A
consuming endpoint passes a typed group handle, the servlet request, and the payload to
`SwitchboardDispatcher`. The dispatcher:

1. Asks the group's **resolver chain** for a use-case key. The chain consults the group's
   consumer-defined resolvers (the spec's "signals") in the configured order, and the first key
   produced wins.
2. Looks the key up in the **registry**, which holds only enabled strategies of that group.
3. Runs the strategy, or raises one of two distinct exceptions. The library's exception handler
   turns those into RFC 9457 problem-details responses: 501 (`USE_CASE_UNRESOLVED`) or 404
   (`USE_CASE_NOT_AVAILABLE`).

Configuration under `switchboard.groups.<group>` controls the resolver order and a per-case kill
switch. Invalid setups fail at startup. Each dispatch is logged at DEBUG. An optional actuator
endpoint (`/actuator/switchboard`) reports groups, order, and each case's enabled and registered
state. The library ships no built-in resolvers, no business logic, and no controllers.

## Technical Context

**Language/Version**: Java 17 (compiled with `--release 17`; also runs on newer LTS JDKs).

**Primary Dependencies**: Spring Boot 4.x, using the latest GA patch at implementation time (no
milestones or RCs), with Spring Framework 7 and Jakarta Servlet 6.1. The autoconfigure module
uses `spring-boot-autoconfigure`, `spring-webmvc`, and `jakarta.servlet-api` (provided). It also
uses Spring Boot actuator autoconfigure (optional) and `spring-boot-configuration-processor`
(optional, annotation processor). The starter adds `spring-boot-starter-webmvc`. All of these are
platform components, pre-approved under Principle VII, so **no third-party library is
introduced**. The transitive dependencies they bring that fall outside the pre-approved list were
reviewed and approved by the project owner (§R17).

**Storage**: N/A. All state is built at startup from beans and configuration, and it is immutable.

**Testing**: JUnit Jupiter, AssertJ, Mockito, and Spring Test (`MockHttpServletRequest`,
`MockMvc`), all managed by the Spring Boot BOM. Auto-configuration tests use
`WebApplicationContextRunner`.

**Target Platform**: Servlet-based Spring Boot 4.x applications on JDK 17+ (HTTP only, FR-037).

**Project Type**: Library, as a Maven multi-module build: parent, autoconfigure, starter, and an
unpublished sample application.

**Performance Goals**: Resolution plus registry lookup must add less than 1 ms at p95, excluding
strategy and resolver work (SC-008). The expected overhead is in microseconds: one ordered list
walk and two hash lookups.

**Constraints**:
- There is no runtime reconfiguration (FR-018).
- Every library bean is `@ConditionalOnMissingBean` (FR-029).
- No payload contents may appear in logs or error details (FR-027, FR-036).
- There are no dependencies beyond the platform (Principle VII).
- There is no AOP, because `@Aspect` would pull in AspectJ, a third-party library (§R9).

**Scale/Scope**: About 15 public types. Consuming applications typically have 1–5 groups and a few
to a few dozen use cases per group. Lookups are O(1) per group, and the resolver walk is O(n) in
the number of resolvers in the group.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design (see the bottom of this
section). Checked against constitution **v2.0.1** (amended 2026-11-15).*

| # | Principle | Gate | Status |
|---|-----------|------|--------|
| I | Single library, two modules, lockstep release | `switchboard-autoconfigure` holds all the code; the starter is a pom-only module; both share one version from one parent. Boot 4.x only, Java 17+, no compatibility shims. The sample module is never deployed. | PASS |
| II | Framework vs. business logic | The library ships the registry, resolver chain, dispatcher, config binding, error mapping, and endpoint. Strategies, resolvers, and controllers live only in the consumer (or the sample). Every bean is `@ConditionalOnMissingBean`. | PASS |
| III | Configuration-first design | Per dispatch group, `switchboard.groups.<group>.cases.<key>.enabled` controls use-case enablement and `switchboard.groups.<group>.resolution-order` controls which resolvers run and in what order. Settings specific to a resolver, such as a header name, belong to the consumer's own resolvers. Changes apply on restart; there is no `@RefreshScope` and no flag service. | PASS |
| IV | Error handling | 501 for unresolved and 404 for unavailable, via two distinct exception types under one sealed parent. They are never merged. | PASS |
| V | Code quality (Effective Java) | `requireNonNull` in every constructor. Duplicate keys or resolver names fail at startup, naming both classes. `Map.copyOf` / `List.copyOf` for all stored collections. One scoped `@SuppressWarnings("unchecked")` in `dispatch`. `Optional` only for single values; collections are returned empty, never wrapped. | PASS |
| VI | Test-driven development | `tasks.md` must place every test task before the implementation task it covers. Bug fixes start from a reproducing test. | PASS (enforced in `/speckit-tasks`) |
| VII | Third-party dependency discipline | Only the JDK, Spring Boot 4.x platform modules, and BOM-managed test libraries are used. JMH (for SC-008) and AspectJ (for AOP logging) were considered and **not** adopted (§R9, §R12). Five transitive dependencies of Spring itself and of `spring-boot-starter-webmvc` were found by the T062 audit and approved (§R17). | PASS |
| — | Testing standards | Unit tests run without Spring for the registry, chain, dispatcher, handler, and endpoint report. `ApplicationContextRunner` covers activation, back-off, and overrides. The sample app has real strategies and resolvers. | PASS |
| — | Publishing | `com.llizzard` group, internal Nexus only, version 2.0.0. | PASS. The Nexus URL is still to be provided (§R13). |

**Post-design re-check (after Phase 1)**: unchanged. The contracts add no beans without
`@ConditionalOnMissingBean`. The only unchecked cast is scoped and justified in
[contracts/java-api.md](contracts/java-api.md). The startup validator keeps every
misconfiguration from SC-006 out of request time. All gates pass, with no deviations to justify.

## Project Structure

### Documentation (this feature)

```text
specs/001-use-case-dispatch/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R16
├── data-model.md        # Phase 1: entities, validation rules, dispatch outcome matrix
├── quickstart.md        # Phase 1: runnable validation scenarios mapped to success criteria
├── contracts/
│   ├── java-api.md          # Public types a consuming developer codes against
│   ├── configuration.md     # switchboard.* property schema and startup validation rules
│   ├── http-errors.md       # 501/404 problem-details bodies
│   └── actuator-endpoint.md # /actuator/switchboard report schema
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks), not created here
```

### Source Code (repository root)

```text
pom.xml                                   # switchboard-parent (packaging pom, published)
switchboard-autoconfigure/
├── pom.xml
└── src/
    ├── main/java/com/llizzard/switchboard/
    │   ├── SwitchboardGroup.java             # typed group handle (name + request/response types)
    │   ├── SwitchboardStrategy.java          # consumer business logic contract
    │   ├── SwitchboardResolver.java          # consumer "signal" contract
    │   ├── SwitchboardContext.java           # record: group, caseKey, resolverName
    │   ├── SwitchboardResolution.java        # record: caseKey, resolverName
    │   ├── SwitchboardDispatcher.java
    │   ├── SwitchboardRegistry.java
    │   ├── SwitchboardResolverChain.java
    │   ├── SwitchboardDispatchException.java # sealed parent
    │   ├── SwitchboardUnresolvedException.java
    │   ├── SwitchboardNotAvailableException.java
    │   ├── autoconfigure/
    │   │   ├── SwitchboardAutoConfiguration.java
    │   │   ├── SwitchboardEndpointAutoConfiguration.java
    │   │   ├── SwitchboardProperties.java
    │   │   └── SwitchboardStartupValidator.java
    │   ├── web/
    │   │   └── SwitchboardExceptionHandler.java
    │   └── actuate/
    │       ├── SwitchboardEndpoint.java
    │       └── SwitchboardReport.java        # records: report, group report, case report
    ├── main/resources/META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/java/com/llizzard/switchboard/
        ├── (unit)          SwitchboardGroupTest, SwitchboardRegistryTest,
        │                   SwitchboardResolverChainTest, SwitchboardDispatcherTest,
        │                   SwitchboardDispatchPerformanceTest
        ├── autoconfigure/  SwitchboardAutoConfigurationTest, SwitchboardStartupValidationTest,
        │                   SwitchboardPropertiesBindingTest
        ├── web/            SwitchboardExceptionHandlerTest
        └── actuate/        SwitchboardEndpointTest, SwitchboardEndpointAutoConfigurationTest
switchboard-spring-boot-starter/
└── pom.xml                               # no sources: autoconfigure + spring-boot-starter-webmvc
switchboard-sample/                       # not deployed (maven.deploy.skip)
├── pom.xml
└── src/
    ├── main/java/com/llizzard/switchboard/sample/
    │   ├── refunds/   RefundGroup, RefundController, StandardRefundStrategy,
    │   │              ExpeditedRefundStrategy, RefundTypeHeaderResolver, RefundAmountResolver
    │   └── payments/  PaymentGroup, PaymentController, CardPaymentStrategy, PaymentHeaderResolver
    ├── main/resources/application.yml
    └── test/java/com/llizzard/switchboard/sample/
        └── SampleDispatchIT-style MockMvc tests (dispatch, 501/404, kill switch, isolation, endpoint)
```

**Structure Decision**: a Maven multi-module build. Principle I fixes the two published modules,
and the published parent pom carries the shared version and the Spring Boot BOM import. The
sample is a third, unpublished module that serves as the end-to-end proof the constitution's
Testing section requires. Packages separate the consumer-facing API (the root package) from
wiring (`autoconfigure`), web error mapping (`web`), and the optional actuator integration
(`actuate`). That way actuator types are only loaded when actuator is on the classpath.

## Complexity Tracking

No violations. The Principle III deviation recorded in the first version of this plan was resolved
by constitution amendment v2.0.0 (2026-11-15).
