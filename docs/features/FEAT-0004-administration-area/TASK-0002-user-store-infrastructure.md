---
feat: FEAT-0004
adrs: [0002, 0008]
status: todo
depends_on: [TASK-0001]
---

# User-store infrastructure: enabled column + repository operations

Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (hexagonal) and [ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md). Driven adapter + schema for the ports added in [TASK-0001](TASK-0001-account-lifecycle-domain.md).

## Scope
- Migration adding an `enabled` column to the users table, defaulting to `true` so existing accounts stay active.
- Extend `JdbcUserRepository` with `findAll`, an insert for new accounts, and set-enabled.
- Micronaut wiring for the new operations.

## Acceptance criteria
- `findAll` returns every persisted account with email, role, and enabled state. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #5)
- Inserting a new account persists it so it is subsequently found and can authenticate. (SPEC-0003 #6)
- The email column is uniquely constrained: inserting a duplicate email fails without altering the existing row. (SPEC-0003 #7)
- Set-enabled toggles the stored state; a disabled row is denied at authentication and an enabled row is allowed. (SPEC-0003 #8, #9)
- Stored passwords remain salted hashes; no plaintext appears in storage. (SPEC-0003 #12)
- Adapters satisfy the domain port contracts (integration-tested against PostgreSQL).
