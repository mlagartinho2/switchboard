# Feature Specification: Switchboard Use-Case Dispatch Library

**Feature Branch**: `001-use-case-dispatch`

**Created**: 2026-11-14

**Status**: Draft

**Input**: User description: "copilot/input/specify.md — a reusable library that lets an application
register several implementations of the same use-case contract and automatically select the right
one per incoming request, using configurable signals checked in priority order, with a kill switch
per use case, distinct not-implemented / not-found outcomes, overridable defaults, and a runtime
inspection view for operators." Supporting detail taken from `copilot/input/switchboard-spec.md`.

## Clarifications

### Session 2026-11-14

- Q: How does a request payload carry its use-case key? → A: There is no dedicated key field on
  the request. Consuming applications define their own signals that compute the key from any
  request information (payload values, headers, the caller's security context, or a mix). The
  library ships no built-in payload signal.
- Q: Who enforces whether a use case is enabled? → A: The library enforces it, from one standard
  setting per use case.
- Q: Should v1 record each dispatch? → A: Logging only. The library logs each dispatch (key,
  signal, duration, outcome). Metrics are left to consuming applications.
- Q: Should the library ship built-in header and route signals? → A: No. The library ships no
  built-in signals, only the extension point. Every signal (header-based, route-based,
  payload-based, identity-based) is defined by the consuming application.
- Q: When an application has several endpoints with different contracts, what happens when a
  signal produces a key that belongs to another contract? → A: Each contract is its own dispatch
  group, with its own use cases, signals, order, and settings. Keys only need to be unique within
  a group. A key from another group counts as unknown in this group, so the caller gets "not
  found".

### Session 2026-11-15

- Q: What format should the bodies of the 501 and 404 failure responses use? → A: The standard
  HTTP problem-details format (RFC 9457), extended with a machine-readable error code, the group,
  and (for "not found") the key.
- Q: Should v1 dispatch only HTTP requests, or also non-HTTP inputs? → A: HTTP only. Non-HTTP
  inputs (messaging, batch) are out of scope for v1, but the design must not rule them out.
- Q: Can several endpoints that share a contract use the same dispatch group? → A: Yes. A group
  is tied to a contract, not an endpoint. Any number of endpoints with that contract may hand
  requests to the same group and share its use cases, signals, and settings.
- Q: Can one signal be used by several dispatch groups? → A: No. Each signal belongs to exactly
  one group. Selection logic needed by two groups is registered once per group.

## User Scenarios & Testing *(mandatory)*

Three audiences use this library:

- **Consuming developer**: a developer on a team whose application depends on the library. They
  write the business logic for each use case, any custom signals, and the endpoint that receives
  requests.
- **Operator**: the person who configures, deploys, and troubleshoots the consuming application.
- **API caller**: the client that sends requests to the consuming application's endpoint.

### User Story 1 - Requests reach the right use case automatically (Priority: P1)

A consuming developer has one endpoint whose request and response contract is shared by several
use cases (for example "standard refund" and "expedited refund"). Each use case has its own
business logic. The developer writes one implementation per use case and gives each a unique
use-case key. The developer also writes one or more signals that decide how a request identifies
its use case. A signal can compute the key from anything in the request: payload values, headers,
the route, who the caller is, or a combination. The library consults the signals in the
configured priority order, takes the first key produced, and runs the matching implementation.
The endpoint contains no per-use-case branching.

**Why this priority**: This is the library's reason to exist: it replaces the growing if/else
blocks in consuming endpoints. Without it nothing else has value.

**Independent Test**: In a sample application with two use cases behind one endpoint, define two
signals: one that reads a header and one that derives the key from payload values. Send requests
identified through each signal and confirm the correct business logic ran every time. Then add a
third use case by adding only a new implementation and confirm it is reachable with no change to
the library or the endpoint.

**Acceptance Scenarios**:

1. **Given** use cases A and B are registered and a header-reading signal is checked first,
   **When** a request arrives whose header names A, **Then** A's business logic runs and its
   response is returned to the caller.
2. **Given** the order is a header-reading signal, then a payload-reading signal, **When** a
   request's header names A and its payload would produce B, **Then** A runs (the first
   producing signal wins).
3. **Given** a signal that derives B from a combination of payload values, **When** a request
   without the header carries those values, **Then** B runs.
4. **Given** a signal that derives the key from the caller's authenticated identity, **When** an
   authenticated caller sends a request, **Then** the use case mapped to that identity runs.
5. **Given** a working application, **When** a developer adds use case C as a new implementation,
   **Then** requests identified as C reach C, and no library code or endpoint code changed.
6. **Given** A and B return the same response shape, **When** A fills in a field that only A uses
   and B leaves it empty, **Then** both responses are valid and are delivered without error.
7. **Given** an application with a "refunds" group and a "payments" group, each behind its own
   endpoint, **When** a request on the refunds endpoint is identified as a key that exists only in
   the payments group, **Then** the caller receives "not found" and no payments implementation
   runs.

---

### User Story 2 - Callers can tell "unknown request" from "unavailable use case" (Priority: P2)

An API caller sends a request that the application cannot serve. The caller gets one of two
clearly different responses. "Not implemented" means no signal identified any use case. "Not
found" means a use case was identified but no active implementation exists for it, either because
it is switched off or because the key is not recognized. The caller and the operator can act on
the difference.

**Why this priority**: Without a clear, consistent failure response, callers get generic errors
and operators cannot tell a malformed request from a switched-off feature.

**Independent Test**: With no signal producing a key, confirm the caller gets the not-implemented
response. With a signal producing an unknown key, confirm the caller gets the not-found response.
Confirm a caller can tell the two apart from the status and the error code alone.

**Acceptance Scenarios**:

1. **Given** no configured signal produces a use-case key, **When** a request arrives, **Then** the
   caller receives a "not implemented" response with a machine-readable error code specific to
   that situation.
2. **Given** a signal produces key X and no implementation is registered for X, **When** the
   request arrives, **Then** the caller receives a "not found" response with a different
   machine-readable error code naming X.
3. **Given** use case A has an implementation but is switched off, **When** a request is identified
   as A, **Then** the caller receives the same "not found" response as for an unknown key.
4. **Given** either failure response, **When** the consuming developer has written no
   error-handling code of their own, **Then** the response is still produced consistently by the
   library.

---

### User Story 3 - Operators change behavior through configuration only (Priority: P3)

An operator needs to switch a misbehaving use case off, change which signal takes priority, or
stop consulting a signal altogether. The operator edits configuration and restarts or redeploys.
No developer has to change code.

**Why this priority**: The kill switch and adjustable priority are the controls that make the
library safe to run in production. They build on Story 1.

**Independent Test**: Switch a use case off in configuration, restart, and confirm requests for it
now get "not found". Reverse the order of two signals, restart, and confirm a request that both
signals can identify now resolves through the other signal. Remove a signal from the order and
confirm it is no longer consulted.

**Acceptance Scenarios**:

1. **Given** use case A is enabled, **When** the operator sets A to disabled and restarts, **Then**
   requests identified as A receive "not found", and no other use case is affected. No code
   change was needed.
2. **Given** the order is signal S1 then signal S2, **When** the operator reverses it and
   restarts, **Then** a request for which S1 produces A and S2 produces B runs B.
3. **Given** the operator removes signal S3 from the order, **When** a request can only be
   identified by S3, **Then** the caller receives "not implemented".
4. **Given** the operator switches the whole library off, and the consuming application's
   endpoints follow the documented pattern for tolerating that, **When** the application starts,
   **Then** it starts normally and the library provides no dispatch behavior.

---

### User Story 4 - Operators can see what is registered and how routing is set up (Priority: P4)

While troubleshooting ("why did this request go to use case X?"), an operator asks the running
application for a report. The report lists every known use case with whether it is enabled,
whether an implementation is registered, the implementation that handles it, and its owner,
version, and description. Entries are organized by dispatch group, and each group shows its
current signal order. The operator does not need to read source code or configuration files.

**Why this priority**: This speeds up diagnosis, but the library delivers its core value without
it.

**Independent Test**: In a sample application with one enabled use case, one disabled use case,
and one configured use case with no implementation, request the report and confirm all three
appear with correct enabled and registered values, along with the current signal order.

**Acceptance Scenarios**:

1. **Given** the application is running, **When** an authorized operator requests the inspection
   report, **Then** it shows, for each dispatch group, the signal order and one entry per known
   use case in that group.
2. **Given** use case A has an implementation but is disabled, **When** the report is requested,
   **Then** A appears as enabled = false and registered = true.
3. **Given** use case Y has configuration but no implementation, **When** the report is requested,
   **Then** Y appears as enabled = true and registered = false, which explains why requests for Y
   get "not found".
4. **Given** the consuming application does not include the platform's operational-management
   features, **When** it starts, **Then** it starts normally and no inspection report is offered.

---

### User Story 5 - Consuming teams can replace any framework part without forking (Priority: P5)

A consuming team needs different behavior from part of the framework, for example different
selection logic, a different registry, or a different error response format. The team
supplies its own version of that part, and the library uses it in place of the default. The team
does not modify or fork the library.

**Why this priority**: This keeps the library usable by teams with unusual needs. Most teams will
never use it.

**Independent Test**: For each framework part, supply a replacement in a test application and
confirm the replacement is used and the default is not.

**Acceptance Scenarios**:

1. **Given** a consuming application supplies its own version of a framework part, **When** the
   application starts, **Then** the library uses the supplied version and creates no default for
   that part.

---

### Edge Cases

- **Conflicting signals**: when several signals would produce different keys, the one earliest in
  the configured order wins. Later signals are not consulted.
- **Blank values**: a signal that returns an empty or whitespace-only key counts as producing no
  key, and resolution moves on to the next signal.
- **Group without signals**: if a dispatch group has implementations but no signals, the
  application refuses to start, and the message names the group.
- **Group without implementations**: a group with signals but no implementations is allowed. Every
  request to it receives either "not implemented" or "not found".
- **Key from another group**: a key that exists only in a different group is treated as unknown
  in this group, so the caller receives "not found".
- **Same key in two groups**: allowed. The two use cases are independent, including their enabled
  settings.
- **Failing custom signal**: if a consuming application's own signal raises an error, the library
  does not swallow it or turn it into "not implemented". The error reaches the consuming
  application's normal error handling.
- **Duplicate keys**: if two implementations in the same group claim the same use-case key, the
  application refuses to start, and the message names the group, both implementations, and the
  shared key.
- **Duplicate signal names**: if two signals in the same group share a name, the application
  refuses to start, and the message names the group and both signals.
- **Unknown signal in the order**: if a group's configured order names a signal that does not
  exist in that group, the application refuses to start, and the message names the group and the
  unknown signal.
- **Empty order**: if a group's configured order lists no signals at all, the application refuses
  to start, and the message names the group.
- **Unknown group in configuration**: if configuration refers to a group that has no
  implementations and no signals, the application refuses to start, and the message names the
  group.
- **Implementation without configuration**: an implementation with no configuration entry is
  treated as enabled.
- **Configuration without implementation**: such a use case appears in the inspection report and
  requests for it receive "not found".
- **Business-logic failures**: errors raised inside a use case's business logic are not converted
  by the library. They reach the consuming application's normal error handling unchanged.
- **Key matching**: use-case keys are compared exactly, including letter case.

## Requirements *(mandatory)*

### Functional Requirements

**Registration and dispatch**

- **FR-001**: The library MUST let a consuming application register any number of implementations
  that share one request/response contract, each identified by a use-case key that is unique
  within its dispatch group.
- **FR-002**: The library MUST discover registered implementations automatically when the
  application starts. The consuming developer MUST NOT need to list them in a central place or
  edit library code.
- **FR-003**: For each request handed to it, the library MUST determine a use-case key, run the
  matching implementation, and return that implementation's response unchanged.
- **FR-004**: The library MUST let implementations of the same contract leave optional response
  fields empty. Only the overall response shape is shared.
- **FR-005**: The library MUST make the resolved use-case key and the name of the signal that
  produced it available to the implementation while it runs.
- **FR-006**: If two implementations in the same group declare the same use-case key, the
  application MUST refuse to start, and the error MUST name the group, both implementations, and
  the shared key.

**Dispatch groups**

- **FR-032**: Use cases MUST be organized into named dispatch groups, one per request/response
  contract. Every implementation and every signal MUST belong to exactly one group. A signal
  MUST NOT be shared between groups. Selection logic needed by several groups is registered
  separately in each one.
- **FR-033**: When the consuming endpoint hands a request to the library, it MUST name the group
  that handles it. Only that group's signals are consulted, and only that group's implementations
  can run. Any number of endpoints with the same contract MAY hand requests to the same group, and
  they then share its use cases, signals, and settings.
- **FR-034**: Each group MUST have its own signal order and its own per-use-case enabled settings.
  Changing one group's settings MUST NOT affect any other group.
- **FR-035**: An application with a single contract MUST still work with exactly one group and no
  group-related configuration beyond what a single group needs.

**Resolution signals**

- **FR-007**: A signal MUST be a named unit that, given a request, either produces one use-case key
  or produces nothing.
- **FR-008**: The library MUST NOT ship any built-in signals. It provides only the extension point
  through which consuming applications define signals.
- **FR-009**: Consuming applications MUST be able to define any number of named signals. A signal
  MUST be able to compute the key from any information the request offers, including payload
  values, headers, route, and the caller's security context, alone or in combination.
- **FR-010**: The library MUST NOT require the request to carry a dedicated use-case key field,
  header, or route.
- **FR-011**: Within a group, signals MUST be consulted in that group's configured priority order,
  and the first signal that produces a key MUST win.
- **FR-012**: Signal names MUST be unique within a group. A duplicate name MUST stop application
  startup with a message naming the group and both signals.

**Configuration**

- **FR-013**: All of the following MUST be configurable per group without code changes: which
  signals are consulted, their order, and whether each use case is enabled. Settings specific to a
  signal
  (for example which header it reads) belong to the consuming application that defines it.
- **FR-014**: The library MUST enforce whether a use case is enabled, from one standard setting per
  group and use-case key. Disabling a use case MUST NOT require any change to, or annotation on, its
  implementation.
- **FR-015**: A disabled use case MUST be treated exactly like an unknown key when dispatching
  (see FR-021). Its implementation MUST still be reported as registered.
- **FR-016**: A use case with no enabled setting MUST be treated as enabled.
- **FR-017**: The library MUST offer one setting that switches the whole library off. When it is
  off, the library MUST provide none of its components and no dispatch behavior, and it MUST NOT
  by itself prevent the consuming application from starting. Consuming-application components
  that use the library's dispatcher MUST tolerate its absence, for example by obtaining it
  optionally or by switching off together with the library. The library MUST document this
  pattern.
- **FR-018**: Configuration changes MUST take effect on restart or redeploy. Changing behavior
  while the application keeps running is out of scope.
- **FR-019**: Invalid configuration MUST stop application startup with a message naming the
  problem and the group concerned. Invalid configuration means: an unknown signal name in a
  group's order, an empty order, a group with implementations but no signals, or a configured
  group that does not exist.
- **FR-020**: Each use case MAY carry descriptive metadata in configuration: owner, version, and
  description.

**Failure responses**

- **FR-021**: When a key is resolved but no enabled implementation is registered for it, the
  caller MUST receive a "not found" response (HTTP 404) with an error code that names the key.
- **FR-022**: When no signal produces a use-case key, the caller MUST receive a "not implemented"
  response (HTTP 501) with a different error code identifying that situation.
- **FR-023**: The library MUST produce both failure responses for the consuming application. The
  consuming developer MUST NOT need to write error-handling code to get them.
- **FR-036**: Both failure response bodies MUST use the standard HTTP problem-details format
  (RFC 9457): type, title, status, and detail. They MUST add three extra fields: the error code,
  the group, and, for "not found" only, the resolved key. The detail text MUST NOT include
  request payload contents.
- **FR-024**: The two failure situations MUST stay distinguishable to the caller and MUST NOT be
  merged into one generic error.

**Observability**

- **FR-025**: The library MUST offer operators a read-only report, organized by group. For each
  group it MUST show the current signal order and, for every use case that is configured or
  registered in that group, its key, enabled state, registered state, handling implementation
  (if any), owner, version, and description.
- **FR-026**: The report MUST be offered only when the consuming application includes the
  platform's operational-management features, and it MUST follow that platform's normal rules
  for enabling and exposing operational endpoints. Restricting access to the report is the
  consuming application's responsibility.
- **FR-027**: The library MUST log every dispatch attempt with the group, the resolved key (if
  any), the signal that produced it, the outcome, and the elapsed time. The outcome is one of:
  handled, not found, not implemented, or failed (the use case's own logic raised an error,
  which is still passed on unchanged). Logs MUST be at a verbosity that is off by default in
  production, and MUST NOT include request payload contents.
- **FR-028**: The library MUST NOT emit metrics in v1. Metrics are left to consuming applications.

**Extensibility and boundaries**

- **FR-029**: Every component the library provides by default MUST be replaceable by a consuming
  application's own version without changing or forking the library.
- **FR-030**: The library MUST NOT contain business logic, custom signals, or the endpoint
  definition. All three belong to the consuming application.
- **FR-031**: Running more than one implementation of the same use case side by side (for example
  v1 and v2) is out of scope. The design MUST leave room to add it later as an extra signal,
  without changing how dispatch works.
- **FR-037**: v1 MUST dispatch HTTP requests only. Dispatching non-HTTP inputs (for example queue
  messages or batch jobs) is out of scope. The two failure situations MUST still be represented
  as distinct errors inside the library before they become HTTP responses, so that a non-HTTP
  path can be added later without redesigning dispatch.

### Key Entities

- **Dispatch group**: a named set of use cases that share one request/response contract,
  together with its own signals, resolution order, and settings. Each endpoint hands its requests
  to exactly one group, and several endpoints with the same contract may share a group.
- **Use case**: a unit of business behavior that a request can be dispatched to. It belongs to one
  group, has a key unique within that group, an enabled state, and optional owner, version, and
  description.
- **Use-case implementation**: the consuming application's business logic for one use case. It
  declares exactly one group and one key, and implements that group's request/response contract.
- **Signal**: a named way of turning a request into a use-case key, always defined by the
  consuming application. It belongs to one group. For a given request it produces one key or
  nothing.
- **Resolution order**: a group's configured, ordered list of signal names consulted for every
  request to that group.
- **Dispatch context**: what an implementation can see about how it was selected: the group, the
  resolved key, and the name of the signal that produced it.
- **Inspection report**: a read-only snapshot, organized by group, of each group's resolution
  order and of every known use case's configured, registered, and descriptive state.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Adding a new use case to a consuming application requires 0 changes to library code
  and 0 changes to the endpoint, and touches only one new implementation plus at most one
  configuration entry.
- **SC-002**: A team migrating an existing branching endpoint ends up with 0 per-use-case branches
  in its endpoint code. The selection logic lives in signals instead.
- **SC-003**: Switching a use case off takes exactly one configuration change and a restart, with 0
  code changes. After that, 100% of requests identified as that use case receive "not found" and
  0 other use cases change behavior.
- **SC-004**: In 100% of test cases, a caller can tell "not implemented" from "not found" using the
  response status and error code alone.
- **SC-005**: An operator can answer "which use cases are registered and enabled, and in what order
  are signals checked?" in under 1 minute with a single request, without opening source code or
  configuration files.
- **SC-006**: 100% of the defined misconfigurations are caught when the application starts, and 0
  surface for the first time while handling a request. These are: duplicate keys or signal names
  within a group, an unknown signal, an empty order, a group with implementations but no signals,
  and a configured group that does not exist.
- **SC-010**: In an application with two groups, 0 requests sent to one group's endpoint ever reach
  an implementation from the other group.
- **SC-007**: Every default framework component can be replaced by a consuming application, as
  shown by one passing replacement test per component, with 0 forks of the library.
- **SC-008**: Selecting and handing off to a use case adds less than 1 millisecond per request at
  the 95th percentile, not counting the business logic or any custom signal's own work.
- **SC-009**: A minimal sample application uses at least one real use case end to end, including
  at least one custom signal, and serves as a working reference for consuming teams.

## Assumptions

- **Platform**: consuming applications run Spring Boot 4.x on Java 17 or later. Older platform
  versions are deliberately unsupported (see the constitution, Principle I).
- **Packaging**: the library ships as two modules released together under one version, starting
  at 2.0.0, and is published only to the internal repository under group `com.llizzard`.
- **Signals**: the library ships no built-in signals (see Clarifications). This matches
  constitution Principle III (v2.0.0): the library's required settings are per-group use-case
  enablement and signal order, and any signal-specific setting such as a header name belongs to
  the consuming application.
- **Default order**: when no order is configured for a group, the library consults all of that
  group's signals in the consuming application's declared order. Once an order is configured, only
  the signals it names are consulted.
- **Security context**: the library does not itself depend on any security framework. Custom
  signals that need the caller's identity obtain it from the consuming application's own security
  setup.
- **Error codes**: the two error codes are fixed, stable identifiers, one per failure situation
  (for example `USE_CASE_UNRESOLVED` and `USE_CASE_NOT_AVAILABLE`). Their final spelling is
  settled in planning and must not change after release.
- **Inspection data**: the inspection report shows class names and the list of disabled use cases.
  It is therefore sensitive, and consuming applications are expected to restrict it to internal or
  ops access, as they do for other operational endpoints.
- **Inspection filters**: filtering the report (for example "enabled only") is not required in v1.
- **Third-party libraries**: no library beyond the platform is assumed. Any additional library
  needs research and approval before it enters the design (see the constitution, Principle VII).
- **Consumers**: consuming teams still on older platform versions have no upgrade path through
  this library. This is an accepted scope decision.
