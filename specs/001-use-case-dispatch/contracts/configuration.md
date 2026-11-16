# Contract: Configuration (`switchboard.*`)

All settings are read once at startup. Changes require a restart or redeploy (FR-018). Bound to
`SwitchboardProperties` (an immutable record with defensive copies), with IDE metadata generated
by `spring-boot-configuration-processor`.

## Schema

```yaml
switchboard:
  enabled: true                      # default true; false → every Switchboard auto-config backs off (FR-017)
  groups:
    <group-name>:                    # lower-case kebab-case, must match a SwitchboardGroup name
      resolution-order:              # optional; absent → all group resolvers in bean order (§R6)
        - <resolver-name>            #   present → only these, in this order; must be non-empty
      cases:
        "[<CASE_KEY>]":              # always bracket: the key then binds exactly as written (§R5)
          enabled: true              # default true; false → 404 for this key (FR-014/015)
          owner: <text>              # optional metadata, shown in the report (FR-020)
          version: <text>
          description: <text>
```

## Example

```yaml
switchboard:
  groups:
    refunds:
      resolution-order: [refund-type-header, refund-amount]
      cases:
        "[STANDARD]":
          owner: payments-team
          version: "1.0"
          description: Standard refund flow
        "[EXPEDITED]":
          enabled: false             # kill switch: no code change
    payments:
      cases:
        "[STANDARD]":                # same key as refunds/STANDARD; fully independent
          owner: payments-team
```

## Validation (startup)

| Condition | Result |
|---|---|
| `resolution-order` names a resolver not in that group | startup fails: group + name |
| `resolution-order: []` | startup fails: group |
| `groups.<g>` configured, but no strategy or resolver uses group `g` | startup fails: group |
| a group has strategies but no resolvers | startup fails: group |
| `cases.<key>` has no registered strategy in that group | WARN log (allowed; report shows `registered: false`) |

Full list: [data-model.md](../data-model.md) (rules V1–V9, W1).

## Operator notes

- Always write case keys in brackets. Brackets make the key bind exactly as written. Without
  them, a key in a YAML or properties file keeps its upper case and underscores, but loses any
  other character and is split at a `.`. Valid case keys contain neither, so this is a safeguard.
- Case keys are matched exactly and case-sensitively. A configured key that matches no strategy in
  its group (a typo, for example) starts normally and logs a W1 warning naming the group and key.
- Environment variables cannot express case-sensitive or underscored map keys. For per-case
  overrides from the environment, use `SPRING_APPLICATION_JSON`, for example
  `{"switchboard":{"groups":{"refunds":{"cases":{"[EXPEDITED]":{"enabled":false}}}}}}`, or an
  external config file.
- Endpoint access and exposure use Boot's standard `management.*` properties (see
  [actuator-endpoint.md](actuator-endpoint.md)). There is no `switchboard.*` property for them.
