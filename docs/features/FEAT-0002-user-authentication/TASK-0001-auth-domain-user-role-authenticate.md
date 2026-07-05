---
feat: FEAT-0002
adrs: [0002, 0005]
status: done
depends_on: []
---

# Auth domain: User, Role and authenticate use case

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md) (session-based authentication). Domain only — no transport or persistence.

## Scope
- `User` (identity, password hash, role) and `Role` (`USER`, `ADMIN`).
- `UserRepository` and `PasswordEncoder` ports.
- Authenticate use case: find user by email, verify password against stored hash, return role or an indistinct failure.

## Acceptance criteria
- Authenticate succeeds only for a known email with a matching password.
- Failure is indistinct: unknown email and wrong password are not separable (no field-specific signal); password check is not short-circuited when the email is unknown. (SPEC-0002 #3)
- No plaintext password is stored, logged, or returned. (SPEC-0002 #9)
- Unit-tested without a database or HTTP server.
