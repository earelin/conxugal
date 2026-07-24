---
feat: FEAT-0004
adrs: [0002, 0005, 0006, 0010]
status: todo
depends_on: []
---

# Current-user endpoint

Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (hexagonal), [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved `/api/` prefix), [ADR-0005](../../architecture/0005-session-based-authentication.md) (session auth) and [ADR-0010](../../architecture/0010-design-first-openapi-contract.md) (design-first OpenAPI contract). A self-contained vertical letting any authenticated caller read their own identity — this is what [TASK-0005](TASK-0005-admin-ui-shell-and-dashboard.md) needs to gate the admin nav client-side; the server-side `@Secured` rules remain the real gate regardless. Unlike the rest of this feature, the capability itself belongs to authentication, not administration: its acceptance criteria trace to [SPEC-0002](../../specs/SPEC-0002-user-authentication.md) R14, not SPEC-0003.

## Scope
- `FindCurrentUser` domain use case resolving the authenticated caller's `User` by email.
- `GET /api/me`, `@Secured(IS_AUTHENTICATED)` — available to `USER` and `ADMIN` alike, unlike the `/api/admin/*` endpoints — conforming to the [OpenAPI contract](../../api/openapi.yaml).

## Acceptance criteria
- An authenticated `USER` or `ADMIN` receives 200 with their own id, email, role, creation date, and last login; an unauthenticated caller is denied with 401. ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #11)
- The response contains no other account's data, no `enabled` state, and no password value.
- Integration-tested for both roles and the unauthenticated case.
