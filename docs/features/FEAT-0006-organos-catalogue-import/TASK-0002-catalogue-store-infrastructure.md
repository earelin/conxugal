---
feat: FEAT-0006
domain: backend
adrs: [0002, 0008]
status: done
depends_on: [TASK-0001]
---

# Catalogue store infrastructure: migration + JDBC repository

The schema and driven adapter for the port added in
[TASK-0001](TASK-0001-organo-domain-model-and-repository-port.md). Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md);
JDBC/SQL stays entirely in `infrastructure`.

## Scope
- Migration creating the `organo_contratacion` table: UUID primary key, `source_key`
  (`NOT NULL`, **unique**), `name` (`NOT NULL`), nullable `acronym`, `active` (`NOT NULL`,
  default `true`).
- Micronaut Data JDBC implementation of `OrganoRepository`: `findAll`, `findBySourceKey`
  / bulk lookup, insert, update-in-place (name/acronym/active on the matched row), and
  `setActive`.

## Acceptance criteria
- `source_key` is uniquely constrained: inserting a second row with an existing source key
  fails without altering the existing row (idempotency enforced at the store level).
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #7)
- `findAll` returns every stored Órgano with its name, acronym, and active state.
  (SPEC-0004 #3, #8)
- The update path modifies name/acronym/active **on the existing row** (matched by source
  key), never delete-and-reinsert, so any other column on the row is preserved across the
  update. (SPEC-0004 #4, #5)
- `setActive` toggles the stored `active` flag. (SPEC-0004 #6)
- The adapter satisfies the domain port contract, integration-tested against PostgreSQL
  (Testcontainers).
