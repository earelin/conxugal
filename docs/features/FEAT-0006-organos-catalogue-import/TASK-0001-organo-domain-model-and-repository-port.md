---
feat: FEAT-0006
domain: backend
adrs: [0002, 0008]
status: todo
depends_on: []
---

# Órgano domain model + repository port

The `OrganoDeContratacion` aggregate and its repository port. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) (hexagonal); the aggregate
carries its own persistence-mapping annotations for a 1:1 single-table mapping per
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md).
Domain only — no JDBC, SQL, connections, or transport.

## Scope
- `OrganoDeContratacion` domain aggregate: a system-assigned `id` (UUID) identity, a
  `sourceKey` (the stable key reconciliation matches on), a `name`, an optional `acronym`,
  and an `active` boolean. It carries the Micronaut Data mapping annotations
  (`@MappedEntity`, `@Id`, etc.) for its single table.
- `OrganoRepository` port (in `domain`): `findAll()`, `findBySourceKey(...)` (or a bulk
  `findAllBySourceKeyIn(...)` for reconciliation), `insert(...)`, an **update-in-place**
  for an existing row's name/acronym/active, and `setActive(...)`.
- The port shape must let reconciliation add, refresh, deactivate, and reactivate by
  matching on `sourceKey` **without** any delete-and-reinsert.

## Acceptance criteria
- The aggregate carries a UUID identity, an opaque stable `sourceKey`, a `name`, an
  optional `acronym`, and an `active` state. ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #4)
- The repository port exposes read-all, lookup-by-source-key, insert, in-place update, and
  set-active — sufficient to add new, refresh existing in place, deactivate absent, and
  reactivate returning bodies without deleting rows. (SPEC-0004 #4, #5, #6)
- The identity (UUID) is distinct from the `sourceKey`, so a source-side rename does not
  change the aggregate's identity. (SPEC-0004 #4, #5)
- Unit-tested without a database or HTTP server.
