---
feat: FEAT-0004
adrs: [0002, 0005, 0008]
status: todo
depends_on: []
---

# Account-lifecycle domain: enabled state + list/create/set-enabled use cases

Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (hexagonal) and [ADR-0005](../../architecture/0005-session-based-authentication.md) (session auth). Domain only — no transport or persistence; the `enabled` field carries its persistence-mapping annotation per [ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md).

## Scope
- Add an `enabled` boolean and a `createdAt` timestamp to `User`.
- Extend the `UserRepository` port: `findAll()`, an insert for new accounts, and a set-enabled operation.
- Add a `PasswordGenerator` domain service drawing from a cryptographically secure RNG (`SecureRandom`, injected) that produces a random initial password meeting the fixed strength policy: at least 16 characters mixing uppercase, lowercase, digits, and symbols.
- `ListUsers` use case: return every account (email, role, enabled, createdAt).
- `SetUserEnabled` use case: enable/disable an account; refuse to disable the last enabled `ADMIN`, checking the count in the same transaction as the update.
- `Authenticate`: deny a disabled account **after** the password check, so the outcome is indistinct from a wrong password.

## Acceptance criteria
- `ListUsers` returns all accounts — enabled and disabled — each with email, role, state, and creation date. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #5)
- A disabled account fails authentication with the same indistinct failure as a wrong password; the password check is not short-circuited. (SPEC-0003 #8; SPEC-0002 #3)
- Re-enabling a disabled account restores successful authentication. (SPEC-0003 #9)
- No use case removes an account; a disabled account is still returned by `ListUsers` and can be re-enabled. (SPEC-0003 #10)
- `SetUserEnabled` refuses to disable the only remaining enabled `ADMIN`. (SPEC-0003 #11)
- The generated password is readable only in the dto result — never stored, logged, or retrievable by any later use case. (SPEC-0003 #12, #14)
- Unit-tested without a database or HTTP server.
