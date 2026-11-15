# Data Model: Switchboard Use-Case Dispatch Library

The library stores no data. Its "model" is the set of objects built at startup from consumer
beans and configuration, plus the values that flow through one dispatch. Everything below is
immutable once built (Principle V).

## Entities

### SwitchboardGroup&lt;Req, Res&gt; (spec: Dispatch group)

| Field | Type | Rules |
|---|---|---|
| `name` | String | Required; matches `[a-z0-9]+(-[a-z0-9]+)*`; unique per contract |
| `requestType` | `Class<Req>` | Required |
| `responseType` | `Class<Res>` | Required |

- It is created with `SwitchboardGroup.of(name, requestType, responseType)`, which validates
  eagerly (Item 49).
- Equality covers all three fields. Two handles with the same `name` but different types are a
  startup error.
- Relationships: a group has 0..n strategies and 0..n resolvers, has one `GroupSettings`
  (possibly defaulted), and can be used by any number of consumer endpoints (FR-033).

### SwitchboardStrategy&lt;Req, Res&gt; (spec: Use-case implementation)

| Member | Rules |
|---|---|
| `group()` | Returns the group handle; must be the same group on every call |
| `caseKey()` | Matches `[A-Za-z0-9_-]+`; unique within the group (FR-006); compared case-sensitively |
| `execute(Req, SwitchboardContext)` | Consumer business logic; exceptions propagate unchanged |

### SwitchboardResolver&lt;Req&gt; (spec: Signal)

| Member | Rules |
|---|---|
| `group()` | Returns the group handle; exactly one group per resolver (no sharing) |
| `name()` | Non-blank; unique within the group (FR-012) |
| `resolve(HttpServletRequest, Req)` | Returns `Optional<String>`. Empty, blank, or whitespace-only means "no key". Exceptions propagate unchanged. |

### GroupSettings (configuration, `switchboard.groups.<name>`)

| Field | Type | Default | Rules |
|---|---|---|---|
| `resolutionOrder` | `List<String>` (nullable) | absent, meaning bean order | If present: non-empty, and every name exists in the group |
| `cases` | `Map<String, CaseSettings>` | empty | Keys use bracket notation in YAML; a key with no strategy logs a WARN |

### CaseSettings (configuration, `…cases.<key>`)

| Field | Type | Default |
|---|---|---|
| `enabled` | boolean | `true` |
| `owner` | String | null |
| `version` | String | null |
| `description` | String | null |

### SwitchboardResolution (resolver chain output)

`record SwitchboardResolution(String caseKey, String resolverName)`. It is produced only when a
resolver returns a non-blank key. The key is passed through unchanged (not trimmed or
case-folded).

### SwitchboardContext (spec: Dispatch context)

`record SwitchboardContext(String group, String caseKey, String resolverName)`. It is passed to
`execute` (FR-005).

### Dispatch exceptions

| Type | Fields | Maps to |
|---|---|---|
| `SwitchboardDispatchException` (sealed, abstract) | `group` | — |
| `SwitchboardUnresolvedException` | `group` | 501, `USE_CASE_UNRESOLVED` |
| `SwitchboardNotAvailableException` | `group`, `caseKey` | 404, `USE_CASE_NOT_AVAILABLE` |

### SwitchboardReport (spec: Inspection report)

`SwitchboardReport { Map<String, GroupReport> groups }` → `GroupReport { List<String>
resolutionOrder, List<CaseReport> cases }` → `CaseReport { key, enabled, registered,
strategyClass, owner, version, description }`. Schema:
[contracts/actuator-endpoint.md](contracts/actuator-endpoint.md).

## Derived state: a use case's enabled and registered values

A use case in a group is the union of its configured keys and its registered strategy keys.

| Registered strategy? | `enabled` setting | Report shows | Dispatch outcome for that key |
|---|---|---|---|
| yes | absent / `true` | enabled=true, registered=true | strategy runs (200 from consumer) |
| yes | `false` | enabled=false, registered=true | 404 `USE_CASE_NOT_AVAILABLE` |
| no | `true` / absent (config entry exists) | enabled=true, registered=false (startup WARN) | 404 |
| no | `false` | enabled=false, registered=false | 404 |
| no, and no config | — | not listed | 404 |

There are no runtime state transitions. Changing any value requires a restart (FR-018).

## Dispatch flow (per request)

```text
dispatch(group, http, payload)
  └─ chain.resolve(group, http, payload)
       ├─ empty ───────────────────────────────▶ SwitchboardUnresolvedException(group)   → 501
       └─ Resolution(key, resolver)
            └─ registry.find(group, key)
                 ├─ empty ─────────────────────▶ SwitchboardNotAvailableException(group, key) → 404
                 └─ strategy.execute(payload, Context(group, key, resolver)) ─▶ Res
```

Every branch ends with one DEBUG log entry recording group, key, resolver, outcome, and elapsed
time.

## Startup validation rules (all abort startup with `IllegalStateException`)

| # | Rule | Checked by | Spec |
|---|---|---|---|
| V1 | Duplicate `caseKey` in a group (names group, both classes, key) | Registry | FR-006 |
| V2 | `caseKey` not matching `[A-Za-z0-9_-]+` | Registry | R5 |
| V3 | Duplicate resolver `name` in a group | Resolver chain | FR-012 |
| V4 | `resolution-order` names an unknown resolver | Resolver chain | FR-019 |
| V5 | `resolution-order` present but empty | Resolver chain | FR-019 |
| V6 | Group has strategies but no resolvers | Startup validator | FR-019 |
| V7 | Configured group has neither strategies nor resolvers | Startup validator | FR-019 |
| V8 | Same group name declared with different request/response types | Registry, chain, validator | R2 |
| V9 | Invalid group name format | `SwitchboardGroup.of` | R2 |
| W1 | Configured case key with no registered strategy: **WARN only** | Startup validator | R5 |
