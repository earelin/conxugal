---
feat: FEAT-0004
adrs: [0002, 0005, 0006]
status: todo
depends_on: [TASK-0001, TASK-0002]
---

# User-administration REST endpoints

Governed by [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved `/api/` prefix) and [ADR-0005](../../architecture/0005-session-based-authentication.md) (`@Secured`). Driving adapter over the use cases from [TASK-0001](TASK-0001-account-lifecycle-domain.md); no new domain logic.

## Scope
- `GET  /api/admin/users` — list accounts (email, role, enabled).
- `POST /api/admin/users` — create account (email, role, initial password).
- `POST /api/admin/users/{id}/enabled` — set enabled true/false.
- All endpoints carry `@Secured("ADMIN")`.
- Map domain outcomes to HTTP: duplicate email and last-admin refusal to distinct client errors; success shapes never echo the password.

## Acceptance criteria
- A `USER` (or unauthenticated caller) is denied with 403; an `ADMIN` is allowed. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #1)
- `GET` returns every account with email, role, and state. (SPEC-0003 #5)
- `POST` creating a valid account makes it appear in the list and able to authenticate. (SPEC-0003 #6)
- `POST` with an already-existing email is rejected and the existing account is unchanged. (SPEC-0003 #7)
- The enabled endpoint disables then re-enables an account, changing whether it can authenticate. (SPEC-0003 #8, #9)
- No endpoint deletes an account; a disabled account remains listed. (SPEC-0003 #10)
- Disabling the only remaining enabled `ADMIN` is refused and that account stays enabled. (SPEC-0003 #11)
- No request or response body exposes a password in recoverable form. (SPEC-0003 #12)
- Integration-tested at the controller boundary, including the `USER`-forbidden case.
