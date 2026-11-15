# Contract: Inspection endpoint (`/actuator/switchboard`)

This is a read-only Spring Boot Actuator endpoint, `id = "switchboard"` (FR-025, FR-026). It is
registered only when actuator is on the classpath, the endpoint is available, and Switchboard is
enabled.

## Enabling and exposing (consumer configuration)

```yaml
management:
  endpoint:
    switchboard:
      access: read-only          # Boot's endpoint access model; `none` turns it off
  endpoints:
    web:
      exposure:
        include: health, switchboard
```

Like any custom endpoint, it is not exposed over HTTP until it is included. Restricting it to
internal or ops access, for example with Spring Security on the management port, is the
consumer's responsibility. Treat it like `/actuator/env`.

## Response: `GET /actuator/switchboard` → 200

```json
{
  "groups": {
    "refunds": {
      "resolutionOrder": ["refund-type-header", "refund-amount"],
      "cases": [
        {
          "key": "STANDARD",
          "enabled": true,
          "registered": true,
          "strategyClass": "com.llizzard.switchboard.sample.refunds.StandardRefundStrategy",
          "owner": "payments-team",
          "version": "1.0",
          "description": "Standard refund flow"
        },
        {
          "key": "EXPEDITED",
          "enabled": false,
          "registered": true,
          "strategyClass": "com.llizzard.switchboard.sample.refunds.ExpeditedRefundStrategy",
          "owner": null,
          "version": null,
          "description": null
        },
        {
          "key": "LEGACY",
          "enabled": true,
          "registered": false,
          "strategyClass": null,
          "owner": "payments-team",
          "version": "0.9",
          "description": "Configured, never deployed"
        }
      ]
    },
    "payments": {
      "resolutionOrder": ["payment-method-header"],
      "cases": [ { "key": "CARD", "enabled": true, "registered": true, "...": "..." } ]
    }
  }
}
```

## Rules

- `groups` contains every group with at least one strategy or resolver. Keys are sorted
  alphabetically for stable output.
- `resolutionOrder` is the **effective** order: the configured list, or the default bean order
  when none is configured.
- `cases` is the union of the registered strategy keys and the configured keys in that group,
  sorted by key.
- `enabled` reflects configuration (default `true`). `registered` reflects whether a strategy
  bean exists. The two are reported separately on purpose.
- `strategyClass` is the user class name, without CGLIB proxy suffixes, or `null` when no
  strategy is registered.
- There are no write operations and no query filters in v1.
