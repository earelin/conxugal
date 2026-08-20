---
feat: FEAT-0014
domain: backend
adrs: [0002, 0008, 0017, 0019]
status: done
depends_on: []
---

# The refresh floor on the per-Órgano import state

**T₁** — how far one Órgano has been *refreshed* through — and the rule that turns it into an
incremental window's floor. Nothing writes it yet and nothing reads it yet:
[TASK-0003](TASK-0003-the-incremental-walk.md) is the walk that does both, and this task is the
durable fact and the arithmetic on their own.

Governed by [ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md), which puts this
state in PostgreSQL and leaves its schema open, and by
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md) —
`ContratosMenoresImportState` maps its own table, so the column lands on the record that already
carries the `@MappedEntity`.

**Why a second instant rather than a setter for the first** is the feature's *The floor is a second
instant, not a rewritten one*. T₀ (`coveredThrough`) is *when the initial import's first window was
taken* and must survive every resumption unchanged; a port carrying an update for it is a port on
which an off-by-one can be written, and the symptom of that off-by-one is publications that fall
outside every future window. So `ContratosMenoresImportStateRepository` still has **no write for
`coveredThrough`**, and this task does not add one.

## Scope

- A migration (next free `V` across `db/migration` **and** `db/migration-local` — `V17` as things
  stand) adding **`refreshed_through TIMESTAMPTZ`, nullable**, to `contrato_menor_import_state`.
  Nullable is the whole design: null means *this Órgano has never had a clean incremental run*, and
  the floor rule below falls back to T₀ for it.
  `ContratosMenoresImportStateMigrationIntegrationTest` pins the table's exact column set with
  `containsExactlyInAnyOrder`, so it moves with the migration.
- The matching field on `ContratosMenoresImportState` — `@Nullable Instant refreshedThrough` — with
  `startedAt(...)` still creating the row without one, because an initial import has refreshed
  nothing.
- **`updateRefreshedThrough(@Id OrganoId organoId, Instant refreshedThrough)`** on
  `ContratosMenoresImportStateRepository`, beside `updateCursorDate` and `updateState` and with the
  same shape: it moves T₁ and touches neither the status, nor the cursor, nor T₀. The JDBC
  repository declares it `REQUIRES_NEW` for the reason the other two do — this write must not
  commit or roll back with anything a caller is inside.
- **The rule**, on the state record itself since it is a function of that state alone:

  ```
  incrementalFloor(lookback) = coalesce(refreshedThrough, coveredThrough) − lookback
  ```

  It answers an **`Instant`** and **borrows no zone**. Turning an instant into a window boundary
  needs the day the source publishes in, and `Europe/Madrid` lives today as a private constant
  inside `ImportOrganoContratosMenores`; each walk converts in the one place that already knows how,
  rather than this rule holding a second copy of the zone that eventually disagrees with it.
- `lookback` arrives as an argument. The
  `conxugal.contratos-menores.import.lookback` property that supplies it lands with the walk that
  reads it, in [TASK-0003](TASK-0003-the-incremental-walk.md).
- **Three pieces of prose go stale with the column and are corrected here**, because each counts
  what the table or the port holds: `ContratosMenoresImportState`'s and `V14`'s *"three more columns
  on `organo_contratacion`"*, the port's *"**Both** writes commit in a transaction of their own"*,
  and the JDBC repository's *"the **two** writes are declared only to carry their propagation"*.
  There will be three.

**Out of scope:** any write of the column (TASK-0003), any read of it over HTTP, and any *última
actualización* caption — a per-Órgano *last refreshed* read is
[SPEC-0007](../../specs/SPEC-0007-monitor-import-runs.md) R15's, as the feature records.

## Acceptance criteria

- A state row created by an initial import has `refreshed_through` null, and every existing row
  keeps working after the migration: the column is added nullable, so nothing backfills it and no
  read of an already-loaded Órgano changes.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #45)
- With `refreshedThrough` null, `incrementalFloor(lookback)` answers `coveredThrough − lookback`.
  That is what makes an Órgano's **first** refresh after its initial import cover everything
  published while that import was walking — days, for a large publisher. (SPEC-0005 #45)
- With `refreshedThrough` set, `incrementalFloor(lookback)` answers `refreshedThrough − lookback`
  and ignores `coveredThrough` entirely. (SPEC-0005 #45)
- `incrementalFloor` answers an `Instant` and its unit tests assert instants: the method's signature
  and body name no `ZoneId` and no `LocalDate`. (The record still holds a `LocalDate cursorDate`,
  which this rule does not touch.)
- `updateRefreshedThrough` moves only that column: an integration test against PostgreSQL
  (Testcontainers) writes it and finds `state`, `cursor_date` and `covered_through` unchanged — so
  a refresh can never disturb the cursor a half-loaded Órgano resumes from. (SPEC-0005 #46)
- `ContratosMenoresImportStateRepository` still exposes **no** way to write `coveredThrough`. T₀ is
  stamped by `insert` and by nothing else, so the fallback floor cannot drift. (SPEC-0005 #45)
- `ContratosMenoresImportStateMigrationIntegrationTest` asserts the table's four columns plus
  `refreshed_through`, and passes.
