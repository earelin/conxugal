---
feat: FEAT-0004
adrs: [0002, 0008]
status: todo
depends_on: [TASK-0001]
---

# User-store infrastructure: enabled column + repository operations

Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (hexagonal) and [ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md). Driven adapter + schema for the ports added in [TASK-0001](TASK-0001-account-lifecycle-domain.md).

## Scope
- Migration adding an `enabled` column (default `true`, so existing accounts stay active) and a `created_at` column (default current timestamp, to backfill existing rows) to the users table.
- Extend `JdbcUserRepository` with `findAll`, an insert for new accounts persisting the id and `createdAt` supplied by the domain, and set-enabled.
- Micronaut wiring for the new operations.

## Acceptance criteria
- `findAll` returns every persisted account with email, role, enabled state, and creation date. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #5)
- Inserting a new account persists it — including its creation timestamp — so it is subsequently found and can authenticate. (SPEC-0003 #6)
- Pre-existing rows are backfilled with a `created_at` value by the column default. (SPEC-0003 #5)
- The email column is uniquely constrained: inserting a duplicate email fails without altering the existing row. (SPEC-0003 #7)
- Set-enabled toggles the stored state; a disabled row is denied at authentication and an enabled row is allowed. (SPEC-0003 #8, #9)
- Stored passwords remain salted hashes; no plaintext appears in storage. (SPEC-0003 #12)
- Adapters satisfy the domain port contracts (integration-tested against PostgreSQL).
