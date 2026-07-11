---
feat: FEAT-0004
adrs: [0002, 0005, 0006, 0010]
status: todo
depends_on: []
---

# System-status probe + endpoint

Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (hexagonal), [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved `/api/` prefix), [ADR-0005](../../architecture/0005-session-based-authentication.md) (`@Secured`) and [ADR-0010](../../architecture/0010-design-first-openapi-contract.md) (design-first OpenAPI contract). A self-contained vertical: domain model + port, driven adapter, and driving endpoint.

## Scope
- `SystemStatus` domain model (overall service state + datastore reachability) and a `SystemStatusProbe` port.
- Driven adapter assembling status per request with a custom probe: a lightweight datastore connectivity check plus coarse runtime info, disclosing no secrets.
- `GET /api/admin/system-status`, `@Secured("ADMIN")`, conforming to the [OpenAPI contract](../../api/openapi.yaml).

## Acceptance criteria
- A `USER` (or unauthenticated caller) is denied with 403; an `ADMIN` is allowed. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #1)
- The response reports overall service state and datastore reachability. (SPEC-0003 #2)
- Status is assembled fresh per request: when the datastore becomes unreachable, a subsequent call reflects the changed state rather than a cached healthy one. (SPEC-0003 #3)
- No secret or credential value (connection string, password, token, key) appears anywhere in the payload. (SPEC-0003 #4)
- Integration-tested including a probe reporting the datastore as unreachable.
