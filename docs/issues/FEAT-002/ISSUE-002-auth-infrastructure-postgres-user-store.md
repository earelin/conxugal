---
feat: FEAT-002
adrs: [0004]
status: todo
depends_on: [ISSUE-001]
---

# Auth infrastructure: PostgreSQL user store + password hashing adapters

Governed by ADR-0004 (session-based authentication). Driven adapters for the domain ports.

## Scope
- PostgreSQL `UserRepository` adapter + schema (users: email, password hash, role).
- Password hashing adapter (salted hash) implementing `PasswordEncoder`.
- Micronaut wiring.

## Acceptance criteria
- A user can be looked up by email; absent email returns empty without error.
- Stored passwords are salted hashes; verification compares hashes, never plaintext.
- Adapters satisfy the domain port contracts (integration-tested against PostgreSQL).
