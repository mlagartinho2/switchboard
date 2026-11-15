<!--
Sync Impact Report
==================
Version change: 2.0.0 → 2.0.1
Bump rationale: PATCH. This clarifies how the duplicate-registration rule in Principle V applies
to dispatch groups. Its purpose (no strategy may silently overwrite another) is unchanged.
Strategies in different groups live in separate registry key spaces and cannot overwrite each
other.

Modified principles:
  - V. Code Quality Baseline (Effective Java) (title unchanged). The "Fail fast on duplicate
    registration" rule is now scoped to the same dispatch group. The same `caseKey()` in two
    different groups is explicitly allowed.

Modified sections: none

Added sections: none

Removed sections: none

Templates requiring updates:
  - .specify/templates/plan-template.md — ✅ no change needed (Constitution Check reads this file at runtime)
  - .specify/templates/spec-template.md — ✅ no change needed
  - .specify/templates/tasks-template.md — ✅ no change needed

Dependent feature artifacts:
  - specs/001-use-case-dispatch: no change needed. The spec (FR-001, FR-006, edge case "Same key
    in two groups"), research.md rule V1, and tasks T015/T022 already apply the group-scoped
    rule. This resolves finding C1 from /speckit-analyze.

Follow-up TODOs: none
-->

# Switchboard Constitution

## Core Principles

### I. Single Library, Two Modules, Lockstep Release

- Switchboard is one repository released as exactly two modules:
  `switchboard-autoconfigure` (all code) and `switchboard-spring-boot-starter`
  (dependency-only, MUST contain no code).
- Both modules MUST always share the same version number and MUST be released together; releasing
  one without the other is prohibited.
- Spring Boot 4.x is the minimum and only supported version. Spring Boot 2.x and 3.x support is
  deliberately retired. Compatibility shims, conditional imports, or dual servlet-API code paths
  for older Boot versions MUST NOT be added.
- Java 17+ only. Modern language features (`instanceof` pattern matching, records, sealed
  interfaces) MAY be used freely; there is no older Java baseline to protect.

**Rationale**: A single version line and a single supported platform eliminate compatibility
matrices and keep the starter a thin, predictable dependency.

### II. Framework vs. Business Logic Boundary

- Switchboard ships only the framework: use-case registry, resolver chain, dispatcher,
  configuration binding, error mapping, and the observability endpoint.
- Business logic (`SwitchboardStrategy` implementations) and the HTTP controller MUST live in the
  consuming application, never in this library.
- Every bean this library registers MUST be annotated `@ConditionalOnMissingBean` so a consumer can
  override any part of the framework without forking it.

**Rationale**: Keeping domain logic out of the library keeps it reusable across teams, and
universal back-off guarantees consumers are never locked into a framework default.

### III. Configuration-First Design

- Anything that could plausibly vary per environment or per team MUST be exposed as Spring
  configuration rather than requiring a code change. At minimum, for each dispatch group:
  enabling/disabling each use case, and which resolvers (signals) are consulted and in what
  priority order.
- Settings specific to a consumer-defined resolver (for example, the header it reads) belong to
  the consuming application, not to this library.
- Configuration changes take effect on restart/redeploy. Runtime (no-restart) toggling is out of
  scope: `@RefreshScope` and feature-flag service dependencies MUST NOT be introduced for this.

**Rationale**: Configuration-driven behavior lets teams adapt the framework without releases,
while restart-only semantics keep the runtime model simple and deterministic.

### IV. Distinct Error Semantics

- A request that no resolver can match MUST return HTTP 501 Not Implemented.
- A request that resolves to a use-case key with no enabled/registered strategy MUST return
  HTTP 404 Not Found.
- These two failure modes MUST use distinct exception types and MUST NOT be collapsed into a single
  generic error.

**Rationale**: "We can't tell what you want" and "we know what you want but it isn't available"
are different operational problems; clients and operators need to tell them apart.

### V. Code Quality Baseline (Effective Java)

- **Eager argument validation** (Item 49): every framework class taking required collaborators
  (`SwitchboardDispatcher`, `SwitchboardResolverChain`, etc.) MUST null-check them with
  `Objects.requireNonNull(x, "message")` at construction. A missing bean MUST fail at wiring time
  with a clear message, never with a later `NullPointerException` at request time.
- **Fail fast on duplicate registration** (Item 49): if two `SwitchboardStrategy` beans in the
  same dispatch group register the same `caseKey()`, `SwitchboardRegistry` MUST throw at startup
  naming the group, both conflicting classes, and the shared key. Silent overwrite (e.g. via an
  unguarded `Collectors.toMap`) is prohibited. The same `caseKey()` in two different groups is
  allowed, because each group has its own key space.
- **Immutable registries and resolver lists** (Items 15, 17): once built,
  `SwitchboardRegistry`'s map and `SwitchboardResolverChain`'s resolver list MUST NOT be mutated
  and MUST be stored as unmodifiable collections (`Map.copyOf` / `List.copyOf`).
- **Defensive copies** (Item 50): any `List`/`Map` passed into a framework constructor (e.g.
  ordered resolvers, Spring-bound config maps) MUST be copied before being stored.
- **Scoped unchecked casts** (Item 27): the generic cast in `SwitchboardDispatcher.dispatch` is
  unavoidable because the registry is keyed by runtime strings. `@SuppressWarnings("unchecked")`
  MUST be placed on the narrowest scope (the local variable, not the method) with a one-line
  comment explaining why it is safe.
- **Optional for single values only** (Item 55): `SwitchboardRegistry.find` and resolver results
  return `Optional<T>` for a single absent value. Collection-returning methods MUST return an
  empty collection instead of `Optional`.

**Rationale**: Misconfiguration surfaces at startup, shared state cannot drift after
construction, and the API stays honest about absence.

### VI. Test-Driven Development (NON-NEGOTIABLE)

- All production code MUST be written test-first, following Red-Green-Refactor: write a test,
  run it and confirm it fails for the expected reason, write the minimum code to pass, then
  refactor with the tests green.
- No production code is merged without the tests that drove it. A behavior change or bug fix
  MUST begin with a test that reproduces the new behavior or the defect.
- Task lists MUST order each test task before the implementation task it covers.

**Rationale**: Tests written first define the contract before the code exists, so they prove
behavior rather than describe whatever was implemented.

### VII. Third-Party Dependency Discipline (NON-NEGOTIABLE)

- A third-party library MUST NOT be added to the design, plan, or build without documented
  research and explicit approval from the project owner first.
- The research MUST cover, at minimum: the problem it solves, alternatives considered (including
  using the JDK or the existing Spring Boot platform), maintenance and release activity,
  license, transitive dependencies, and compatibility with Spring Boot 4.x and Java 17+.
- Approval MUST be requested before the library appears in any plan or design artifact. Work that
  depends on an unapproved library MUST stop until approval is given.
- A library whose only available version is beta or otherwise pre-release (alpha, beta,
  milestone, release candidate, snapshot) MUST NOT be considered, not even as a candidate.
- The platform this constitution already mandates (the JDK, Spring Boot 4.x, and the test
  libraries managed by the Spring Boot dependency BOM) is pre-approved.

**Rationale**: Every dependency a library adds is inherited by all its consumers. Unvetted or
unstable dependencies push risk onto every team that uses Switchboard.

## Testing Standards

- Every `SwitchboardResolver` and every framework component (registry, dispatcher, resolver chain)
  MUST have unit tests that run without a Spring context wherever possible.
- Auto-configuration MUST be tested with `ApplicationContextRunner`, verifying that it activates
  correctly, backs off when disabled via configuration, and respects `@ConditionalOnMissingBean`
  overrides.
- The repository MUST maintain a minimal sample application exercising at least one real strategy
  end-to-end, as the proof that the library works for a consumer.

## Publishing & Distribution

- Artifacts publish to the internal Nexus/Artifactory only. There is no public Maven Central
  distribution, and no OSS licensing requirements apply.
- Coordinates: `com.llizzard:switchboard-autoconfigure` and
  `com.llizzard:switchboard-spring-boot-starter`.
- Library versioning starts at 2.0.0, marking the clean break from the retired Boot 2/3 line. This
  is not a continuation of a 1.x series. Library versions follow semantic versioning.

## Governance

- This constitution supersedes conflicting practices, conventions, or ad-hoc decisions in the
  repository. Its principles are settled and MUST NOT be re-litigated in individual feature specs.
- Amendments are made by updating this file (via `/speckit-constitution`) in a reviewed pull
  request that states the change, its rationale, and any migration impact on existing code.
- Constitution versioning follows semantic versioning:
  - MAJOR: backward-incompatible removal or redefinition of a principle.
  - MINOR: a new principle or section, or materially expanded guidance.
  - PATCH: clarifications, wording, or typo fixes with no semantic change.
- Every plan's Constitution Check and every code review MUST verify compliance with these
  principles. Any deviation MUST be explicitly justified in the plan's Complexity Tracking section
  or rejected.

**Version**: 2.0.1 | **Ratified**: 2026-11-14 | **Last Amended**: 2026-11-15
