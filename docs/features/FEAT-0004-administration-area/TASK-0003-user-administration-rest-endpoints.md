---
feat: FEAT-0004
adrs: [0002, 0005, 0006, 0010]
status: done
depends_on: [TASK-0001, TASK-0002]
---

# User-administration REST endpoints

Governed by [ADR-0006](../../architecture/0006-reserved-api-url-prefix.md) (reserved `/api/` prefix), [ADR-0005](../../architecture/0005-session-based-authentication.md) (`@Secured`) and [ADR-0010](../../architecture/0010-design-first-openapi-contract.md) (design-first OpenAPI contract). Driving adapter over the use cases from [TASK-0001](TASK-0001-account-lifecycle-domain.md); no new domain logic.

## Scope
- `GET  /api/admin/users` — list accounts (email, role, enabled, created date, and last
  login date). The last-login value is the `lastLoginAt` already carried on `User` (from
  [FEAT-0002](../FEAT-0002-user-authentication/README.md), SPEC-0002 R13); the endpoint
  only reads it, and it is null until the account's first successful login.
- `POST /api/admin/users` — create account from email and role only; the server generates the initial password and returns it **once** in the response body.
- `POST /api/admin/users/{id}/enabled` — set enabled true/false.
- All endpoints carry `@Secured("ADMIN")` and conform to the [OpenAPI contract](../../api/openapi.yaml).
- Map domain outcomes to HTTP: duplicate email and last-admin refusal to distinct client errors; only the create response carries the generated password, and no other response echoes it.
- Close the atomicity gap `SetUserEnabled` (TASK-0001) explicitly defers: its last-admin
  guard reads the enabled-admin count and writes the new state as two separate repository
  calls, so two concurrent disable requests could each pass the guard and drop enabled
  admins to zero. Wiring this endpoint to a real transactional boundary (or an atomic
  conditional update in the repository) must close that race before this ships.

## Open questions
- **How to make the last-admin guard atomic.** Candidates: wrap the count-check-then-update
  in a single DB transaction spanning both `SetUserEnabled` calls, or replace them with one
  atomic conditional `UPDATE ... WHERE` in the repository that only succeeds when disabling
  wouldn't drop enabled admins to zero. Decide during this task's design.

## Acceptance criteria
- A `USER` (or unauthenticated caller) is denied with 403; an `ADMIN` is allowed. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #1)
- `GET` returns every account with email, role, state, created date, and last login date
  (null for an account that has never logged in successfully). (SPEC-0003 #5; SPEC-0002 #10)
- `POST` creating a valid account returns the generated initial password once; the account then appears in the list and can authenticate with that password. (SPEC-0003 #6)
- `POST` with an already-existing email is rejected and the existing account is unchanged. (SPEC-0003 #7)
- The enabled endpoint disables then re-enables an account, changing whether it can authenticate. (SPEC-0003 #8, #9)
- No endpoint deletes an account; a disabled account remains listed. (SPEC-0003 #10)
- Disabling the only remaining enabled `ADMIN` is refused and that account stays enabled. (SPEC-0003 #11)
- Two concurrent requests disabling the last two enabled `ADMIN` accounts cannot both
  succeed; at least one enabled `ADMIN` always remains. (SPEC-0003 #11)
- The create request accepts no password; the generated password appears only in the create response and in no other request or response body. (SPEC-0003 #12, #14)
- Integration-tested at the controller boundary, including the `USER`-forbidden case.
