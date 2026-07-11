---
feat: FEAT-0004
adrs: [0002, 0005, 0006, 0010]
status: todo
depends_on: [TASK-0001, TASK-0002]
---

# User-administration REST endpoints

Governed by [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved `/api/` prefix), [ADR-0005](../../architecture/0005-session-based-authentication.md) (`@Secured`) and [ADR-0010](../../architecture/0010-design-first-openapi-contract.md) (design-first OpenAPI contract). Driving adapter over the use cases from [TASK-0001](TASK-0001-account-lifecycle-domain.md); no new domain logic.

## Scope
- `GET  /api/admin/users` — list accounts (email, role, enabled, created date).
- `POST /api/admin/users` — create account from email and role only; the server generates the initial password and returns it **once** in the response body.
- `POST /api/admin/users/{id}/enabled` — set enabled true/false.
- All endpoints carry `@Secured("ADMIN")` and conform to the [OpenAPI contract](../../api/openapi.yaml).
- Map domain outcomes to HTTP: duplicate email and last-admin refusal to distinct client errors; only the create response carries the generated password, and no other response echoes it.

## Acceptance criteria
- A `USER` (or unauthenticated caller) is denied with 403; an `ADMIN` is allowed. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #1)
- `GET` returns every account with email, role, state, and created date. (SPEC-0003 #5)
- `POST` creating a valid account returns the generated initial password once; the account then appears in the list and can authenticate with that password. (SPEC-0003 #6)
- `POST` with an already-existing email is rejected and the existing account is unchanged. (SPEC-0003 #7)
- The enabled endpoint disables then re-enables an account, changing whether it can authenticate. (SPEC-0003 #8, #9)
- No endpoint deletes an account; a disabled account remains listed. (SPEC-0003 #10)
- Disabling the only remaining enabled `ADMIN` is refused and that account stays enabled. (SPEC-0003 #11)
- The create request accepts no password; the generated password appears only in the create response and in no other request or response body. (SPEC-0003 #12, #14)
- Integration-tested at the controller boundary, including the `USER`-forbidden case.
