---
feat: FEAT-0015
domain: backend
adrs: [0008, 0017, 0019]
status: todo
depends_on: [TASK-0003]
---

# The licitacións per-Órgano import state, and the outstanding-record ledger

Two durable facts about one Órgano's licitacións: **how far its history has been loaded**, and
**which of its records failed**. Nothing writes either yet and nothing reads either yet —
[TASK-0015](TASK-0015-single-organo-initial-import.md) is the walk that does both and
[TASK-0023](TASK-0023-the-outstanding-record-ledger-in-the-walk.md) is what drives the ledger. This
task is the state and its rule on their own.

Governed by [ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md), which puts
resumption state in PostgreSQL beside the Órgano rather than on the run, and by
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md) — the
state record maps its own table, exactly as `ContratosMenoresImportState` does. It depends on
[TASK-0003](TASK-0003-licitacion-domain-model.md) for the `LicitacionId` the ledger is keyed by,
under [ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md).

**Why a second table rather than a family column on the first.** The two cursors are different
kinds of thing: contratos menores resume from a **date** inside a windowed walk, licitacións resume
from an **offset** into an ordered listing. One shared table would carry a nullable column for each
plus a discriminator deciding which is meaningful — three columns to express what two tables express
with none. R4's requirement is precisely that neither family's progress can be read as the other's,
and that is easiest to guarantee when there is no shared row to get wrong.

## Scope

- **A migration** (next free `V` across `db/migration` **and** `db/migration-local`, taken at merge
  time) creating:
  - **`licitacion_import_state`**, keyed by `organo_id` and holding the three-state `state TEXT NOT
    NULL` and a **`cursor_offset INT`** — the offset already consumed, nullable because a row is
    created before any page commits.

    **No covered-through instant.** `contrato_menor_import_state` carries T₀ because that family's
    incremental window is measured from it; this family's incremental mode is driven by `modificado`
    ordering instead. A column the incremental feature has not asked for is the speculative
    generality `CLAUDE.md` forbids.
  - **`licitacion_outstanding_record`**, keyed `(organo_id, publication_id)` — and carrying the
    **four listing-sourced fields** the record cannot answer for itself: `publication_date`,
    `last_modified`, `state_code` and `state_label`.

    Those four are here because of a gap that only shows up on the retry path. Per
    [TASK-0003](TASK-0003-licitacion-domain-model.md) they come from the **listing entry**, and a
    ledger retry happens *before* the cursor resumes, with no listing entry in hand and no way to
    ask the source for one row. Storing them with the identifier is one small table's worth of
    duplication; the alternative is a retried procedure that cannot be stored at all.

    It is a **set of identifiers to try once more**, not a retry queue: no attempt count, no
    backoff, no next-attempt time. That machinery answers a problem nobody has measured.
- `LicitacionImportStatus` — `NEVER_STARTED`, `INCOMPLETE`, `COMPLETE` — with `NEVER_STARTED` never
  stored, exactly as its contratos menores sibling: an Órgano with **no row** *is* never started,
  which is what makes R4 fall out with no migration and no re-marking.
- `LicitacionImportMode.of(status)` — `INITIAL` / `RESUMED` / `INCREMENTAL`, an exhaustive switch.
  `INCREMENTAL` is **returned and implemented nowhere**; the orchestrator skips such an Órgano with
  that reason recorded. Duplicating the shipped four-line switch rather than parameterising one rule
  by family is deliberate, on the same reasoning as the second table.
- `LicitacionImportState` (the record) and `LicitacionImportStateRepository` — `insert`,
  `findByOrganoId`, `updateCursorOffset`, `updateState`.
- **`LicitacionOutstandingRecordRepository`**, the ledger's port:
  `record(OrganoId, LicitacionOutstandingRecord)`, `outstandingFor(OrganoId)`,
  `clear(OrganoId, long publicationId)` and **`hasOutstanding(OrganoId)`**, which the completion
  test reads.
- The state writes are declared `REQUIRES_NEW` in the JDBC repository for the reason the contratos
  menores ones are: progress must be visible before a batch's own transaction settles, and a
  bookkeeping failure must never roll imported procedures back.

**Out of scope:** any walk, any listing or record retrieval, the mode's `INCREMENTAL` branch, and
**any change whatever to `contrato_menor_import_state`** — the two families' progress stays
separately stored and separately written.

## Acceptance criteria

- An Órgano with no `licitacion_import_state` row reads as `NEVER_STARTED` and
  `LicitacionImportMode.of` answers `INITIAL` for it — with **no migration and no re-marking**, so
  every Órgano already marked for contratos menores takes the initial mode on the first licitacións
  run that covers it. ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #5)
- An Órgano at `INCOMPLETE` answers `RESUMED`; one at `COMPLETE` answers `INCREMENTAL`. The switch
  is exhaustive over the status, so a fourth value cannot be added without deciding here what it
  imports. (SPEC-0008 #5)
- Writing an Órgano's licitacións state leaves its `contrato_menor_import_state` row — status,
  cursor date, covered-through and refreshed-through — **byte-for-byte unchanged**, and the reverse
  holds. Integration-tested against PostgreSQL (Testcontainers). (SPEC-0008 #5)
- `updateCursorOffset` moves only the offset: the status is unchanged by it, so a resumption cannot
  be turned into a completion by a cursor write.
- **A ledger row read back answers the four listing fields it was written with**, so a retry can
  store the procedure with no listing entry available. A null publication date round-trips as null.
  (SPEC-0008 #41, #44)
- Recording the same `(organo, publicationId)` twice leaves one ledger row — the walk meets the same
  failing record on every run and must not accumulate. Clearing one leaves the others.
- `hasOutstanding` answers false for an Órgano with an empty ledger and true with one entry, which
  is the whole of the completion test
  [TASK-0023](TASK-0023-the-outstanding-record-ledger-in-the-walk.md) applies. (SPEC-0008 #41)
- A migration integration test pins each new table's exact column set with
  `containsExactlyInAnyOrder`, on the precedent of
  `ContratosMenoresImportStateMigrationIntegrationTest`. `publication_id` is `BIGINT`.
