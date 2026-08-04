---
feat: FEAT-0009
domain: backend
adrs: [0002, 0008]
status: done
depends_on: []
---

# Import mark on the Órgano catalogue

The one administrator-managed attribute this feature adds to `OrganoDeContratacion`: whether
its contratos menores are imported. Storage and domain only — no endpoint (that is
[TASK-0002](TASK-0002-mark-administration-api.md)) and no importer. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) and
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md):
the mark is a column on the aggregate's own table, not a side table.

## Scope
- A migration (next free `V` number) adding `importable` to `organo_contratacion`:
  `BOOLEAN NOT NULL DEFAULT FALSE`. The default is the requirement, not a convenience — a
  newly discovered Órgano must never be imported by accident, and the default is what makes
  that true for rows `OrganoReconciler` inserts without naming the column.
- The `importable` field on the `OrganoDeContratacion` record, and `false` in its
  newly-discovered convenience constructor.
- `OrganoRepository` gains:
  - `updateImportable(@Id UUID id, boolean importable)` — sets and clears the mark on an
    existing row, touching nothing else;
  - a read of the Órganos eligible for import — active **and** marked — for the importer
    to enumerate from. Eligibility is `active && importable` (feature *Design*), so the port
    answers that pair rather than the mark alone.
  - `findAllOrderByName()` and `findById(...)` now carry `importable` by virtue of the field.
- **`OrganoReconciler`'s write set is deliberately untouched.** `update(id, name, active)` and
  `updateActive(id, active)` keep their signatures and their columns; no reconciliation path
  reads or writes `importable`. This is the whole of criterion #6 and it is proven by a test,
  not by inspection.
- The JDBC repository implementation and its integration test.

## Acceptance criteria
- A newly inserted Órgano is stored **unmarked**, including one inserted by
  `OrganoReconciler` from a source entry.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #4)
- `updateImportable` sets and clears the mark on the matched row and changes no other column —
  name, active state and taxonomy placement survive both directions. (SPEC-0005 #4)
- Running a full catalogue reconciliation over a catalogue in which some Órganos are marked —
  including entries that are refreshed, deactivated and reactivated by that run — leaves every
  Órgano's `importable` exactly as it was. (SPEC-0005 #6)
- The eligibility read returns exactly the Órganos that are both `active` and `importable`, and
  omits an Órgano that is marked but inactive as well as one that is active but unmarked. This
  is the *precondition* for SPEC-0005 #3; the criterion itself says *after an import run*, and
  is proven by [TASK-0010](TASK-0010-multi-organo-orchestration.md), which has an importer to
  run.
- Integration-tested against PostgreSQL (Testcontainers); the reconciliation-survival case is
  covered where `OrganoReconciler` is already tested.
