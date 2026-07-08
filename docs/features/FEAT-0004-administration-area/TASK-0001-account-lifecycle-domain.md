---
feat: FEAT-0004
adrs: [0002, 0005, 0008]
status: todo
depends_on: []
---

# Account-lifecycle domain: enabled state + list/create/set-enabled use cases

Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (hexagonal) and [ADR-0005](../../architecture/0005-session-based-authentication.md) (session auth). Domain only — no transport or persistence; the `enabled` field carries its persistence-mapping annotation per [ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md).

## Scope
- Add an `enabled` boolean to `User`.
- Extend the `UserRepository` port: `findAll()`, an insert for new accounts, and a set-enabled operation.
- `ListUsers` use case: return every account (email, role, enabled).
- `CreateUser` use case: enforce email uniqueness, store a salted hash via the existing `PasswordEncoder`, never persist or return plaintext.
- `SetUserEnabled` use case: enable/disable an account; refuse to disable the last enabled `ADMIN`, checking the count in the same transaction as the update.
- `Authenticate`: deny a disabled account **after** the password check, so the outcome is indistinct from a wrong password.

## Acceptance criteria
- `ListUsers` returns all accounts — enabled and disabled — each with email, role, and state. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #5)
- `CreateUser` with a valid email/role/password yields an account whose credentials verify. (SPEC-0003 #6)
- `CreateUser` with an already-existing email is rejected and leaves the existing account unchanged. (SPEC-0003 #7)
- A disabled account fails authentication with the same indistinct failure as a wrong password; the password check is not short-circuited. (SPEC-0003 #8; SPEC-0002 #3)
- Re-enabling a disabled account restores successful authentication. (SPEC-0003 #9)
- No use case removes an account; a disabled account is still returned by `ListUsers` and can be re-enabled. (SPEC-0003 #10)
- `SetUserEnabled` refuses to disable the only remaining enabled `ADMIN`. (SPEC-0003 #11)
- A created account's password is never stored, logged, or returned in a recoverable form. (SPEC-0003 #12)
- Unit-tested without a database or HTTP server.
