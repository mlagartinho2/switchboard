# Contract: HTTP failure responses

These are produced by `SwitchboardExceptionHandler` (`@RestControllerAdvice`, highest precedence,
handling only the two Switchboard exceptions). Content type: `application/problem+json` (RFC
9457). The `errorCode` values and `type` URIs are a stable public contract from 2.0.0 onward.

## 501 Not Implemented: no resolver produced a key (FR-022)

```http
HTTP/1.1 501 Not Implemented
Content-Type: application/problem+json
```

```json
{
  "type": "urn:switchboard:error:use-case-unresolved",
  "title": "Use case not resolved",
  "status": 501,
  "detail": "No resolver in group 'refunds' identified a use case for this request.",
  "instance": "/api/refunds",
  "errorCode": "USE_CASE_UNRESOLVED",
  "group": "refunds"
}
```

## 404 Not Found: key resolved but no enabled strategy (FR-021, FR-015)

This covers an unknown key, a disabled case, and a key that belongs to another group.

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json
```

```json
{
  "type": "urn:switchboard:error:use-case-not-available",
  "title": "Use case not available",
  "status": 404,
  "detail": "Use case 'EXPEDITED' in group 'refunds' is not registered or is disabled.",
  "instance": "/api/refunds",
  "errorCode": "USE_CASE_NOT_AVAILABLE",
  "group": "refunds",
  "key": "EXPEDITED"
}
```

## Rules

- A caller can tell the two cases apart by `status` alone, or by `errorCode` alone (SC-004).
- A disabled case and an unknown key produce identical responses. The caller cannot tell them
  apart; the operator can, through the report.
- `detail` never contains payload contents (FR-036).
- Exceptions thrown by consumer strategies or resolvers are **not** handled here. They reach the
  application's normal error handling.
- A consumer can replace the format by defining its own `SwitchboardExceptionHandler` bean.
