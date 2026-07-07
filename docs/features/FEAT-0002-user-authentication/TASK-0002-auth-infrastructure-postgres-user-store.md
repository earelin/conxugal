---
feat: FEAT-0002
adrs: [0002, 0005, 0008]
status: done
depends_on: [TASK-0001]
---

# Auth infrastructure: PostgreSQL user store + password hashing adapters

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md) (session-based authentication). Driven adapters for the domain ports.

## Scope
- PostgreSQL `UserRepository` adapter + schema (users: email, password hash, role).
- Password hashing adapter (salted hash) implementing `PasswordEncoder`.
- Micronaut wiring.

## Acceptance criteria
- A user can be looked up by email; absent email returns empty without error.
- Stored passwords are salted hashes; verification compares hashes, never plaintext; no plaintext appears in storage, logs, or responses ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #9).
- Adapters satisfy the domain port contracts (integration-tested against PostgreSQL).
