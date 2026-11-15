# Quickstart: Validating Switchboard end to end

This guide proves the feature works. It covers building the library, running the sample
application, and checking each success criterion. It refers to the contracts rather than
repeating them.

## Prerequisites

- JDK 17 or later and Maven 3.9 or later
- `curl` (and optionally `jq`)

## 1. Build and run every automated check

```bash
mvn verify
```

Expected: all modules build and every test passes. This covers:

| Suite | Proves |
|---|---|
| Unit tests (registry, chain, dispatcher, handler, endpoint report) | Core rules without Spring; SC-004 |
| `SwitchboardStartupValidationTest` | Every rule V1–V9 fails startup with the group and names in the message; W1 only warns (SC-006) |
| `SwitchboardAutoConfigurationTest` | Activation; full back-off with `switchboard.enabled=false` (FR-017); one override test per bean (SC-007) |
| `SwitchboardEndpointAutoConfigurationTest` | No endpoint without actuator (`FilteredClassLoader`) or when `access: none` (FR-026) |
| `SwitchboardDispatchPerformanceTest` | p95 dispatch overhead < 1 ms (SC-008) |
| Sample `MockMvc` tests | Scenarios 2–7 below, automated (SC-009) |

## 2. Run the sample application

```bash
mvn -pl switchboard-sample -am spring-boot:run
```

The sample (`switchboard-sample/src/main/resources/application.yml`) defines:

- Group **refunds** (`POST /api/refunds`): strategies `STANDARD` and `EXPEDITED`. Resolvers, in
  order: `refund-type-header` (reads `X-Refund-Type`) and `refund-amount` (payload
  `amount > 1000` → `EXPEDITED`).
- Group **payments** (`POST /api/payments`): strategy `CARD` (`CardPaymentStrategy`), resolver
  `payment-method-header` (reads `X-Payment-Method`).
- An actuator exposure that includes `switchboard`.

## 3. Validation scenarios

| # | Request | Expected | Checks |
|---|---|---|---|
| 2 | `curl -s -XPOST localhost:8080/api/refunds -H 'X-Refund-Type: STANDARD' -H 'Content-Type: application/json' -d '{"amount":50}'` | 200, response produced by `StandardRefundStrategy` | US1 scenario 1 |
| 3 | Same request with no header and `{"amount":5000}` | 200 from `EXPEDITED` (the payload-based resolver) | US1 scenario 3 |
| 4 | Header `STANDARD` plus `{"amount":5000}` | 200 from `STANDARD` (the first resolver wins) | US1 scenario 2 |
| 5 | No header and `{"amount":50}` | 501, `errorCode: USE_CASE_UNRESOLVED`, `group: refunds` ([http-errors.md](contracts/http-errors.md)) | US2, FR-022 |
| 6 | Header `X-Refund-Type: NOPE` | 404, `errorCode: USE_CASE_NOT_AVAILABLE`, `key: NOPE` | US2, FR-021 |
| 7 | `POST /api/refunds` with `X-Refund-Type: CARD`, a key that exists only in payments | 404. No payments strategy runs. | FR-033, SC-010 |
| 8 | `curl -s localhost:8080/actuator/switchboard \| jq` | Both groups with order and case states, matching [actuator-endpoint.md](contracts/actuator-endpoint.md) | US4, SC-005 |

## 4. Configuration-only changes (restart between each)

| Change | Expected |
|---|---|
| Set `switchboard.groups.refunds.cases."[EXPEDITED]".enabled: false` | Scenario 3 now returns 404; refunds `STANDARD` and every payments case are unaffected; the report shows `EXPEDITED` with enabled=false, registered=true (SC-003, US3 scenario 1) |
| Set `refunds.resolution-order: [refund-amount, refund-type-header]` | Scenario 4 now runs `EXPEDITED` (US3 scenario 2) |
| Set `refunds.resolution-order: [refund-type-header]` | Scenario 3 now returns 501 (US3 scenario 3) |
| Set `refunds.resolution-order: [does-not-exist]` | The application **fails to start**. The message names group `refunds` and `does-not-exist` (SC-006). |
| Set `switchboard.enabled: false` | The application starts, and the sample controllers back off via `@ConditionalOnProperty` (see research §R11). There is no `/actuator/switchboard`. |

## 5. Adding a new use case (SC-001)

In the sample, add a class `PartialRefundStrategy implements SwitchboardStrategy<RefundRequest,
RefundResponse>` with `group() = RefundGroup.REFUNDS` and `caseKey() = "PARTIAL"`, annotate it
`@Component`, and restart. Then `X-Refund-Type: PARTIAL` returns 200 from the new class. No
library, controller, or configuration file was changed. A configuration entry is needed only for
metadata or to disable the case.

## 6. Consumer checklist when adopting the library

- Declare one `SwitchboardGroup` constant per contract ([java-api.md](contracts/java-api.md)).
- Implement strategies and at least one resolver per group, as Spring beans.
- Write endpoints as one-line `dispatcher.dispatch(GROUP, http, body)` calls.
- Use bracket notation for case keys in YAML ([configuration.md](contracts/configuration.md)).
- If the application must start with `switchboard.enabled=false`, inject
  `ObjectProvider<SwitchboardDispatcher>` or make the controllers conditional (§R11).
- Expose `/actuator/switchboard` only to operators.
