---
feat: FEAT-0015
domain: backend
adrs: [0010, 0017, 0021]
status: todo
depends_on: [TASK-0001, TASK-0016]
---

# `StartMarkedOrganoImport`: one run, both families

R27 requires that marking an Órgano imports **both** contract families "within one run".
[TASK-0001](TASK-0001-per-family-run-coverage.md) made the schema able to say so; this task is the
use case that does it, and it replaces `StartContratosMenoresImport` on the mark's own path.

The shipped path claims one run for one family. Two claims would not work and the reason is not
incidental: the guard admits one import at a time, so the second family's claim would be **refused
by the guard the first one just took**.

## Scope

- **`StartMarkedOrganoImport`** — claims **one** run covering
  `[(Órgano, CONTRATOS_MENORES), (Órgano, LICITACIONS)]`, with `Importer.AMBAS_FAMILIAS`, and runs
  the two families **in R27's fixed order** on the existing import executor.

  **Contratos menores first.** R27 fixes the order so a partly loaded Órgano is always partly loaded
  the same way, and it is that order because contratos menores is the family a marked Órgano most
  often holds nothing of — settling it quickly and leaving the long load last. A SERGAS licitacións
  walk is ~4.7 hours; running it first would mean the quick family waits behind it.
- **Its failure semantics are fixed here, not left to the implementation:**
  - **a failure in the first family does not stop the second.** They are separate coverage rows with
    separate outcomes, and R30's isolation applies between them exactly as it does between Órganos;
  - **the run's verdict is the aggregate**: `SUCCEEDED` when both halves did, `FAILED` when both
    failed, and **`PARTIALLY_SUCCEEDED` whenever they disagree**;
  - **the trigger is refused as a whole** when the guard is held. Never half-claimed, and never one
    family started while the other is refused.
- **`OrganoImportMarkController` calls this instead of `StartContratosMenoresImport`.** The mark
  itself still always applies, and the refusal still travels in the `200` body rather than as a
  `409`.
- **The published text the change falsifies, all of it:** the mark endpoint's `summary` (*"Mark an
  Órgano for **contratos menores** import"*) and its `description` (*"Opt an Órgano into having its
  **contratos menores** imported"*). An earlier draft named only the second. The `MarkOutcome`
  **shape** is unchanged — one run identifier, one optional refusal — because the mark still starts
  exactly one run.
- **The UI copy this falsifies is handed to a named owner**, not dropped:
  [TASK-0025](TASK-0025-the-marks-copy-now-that-it-means-both-families.md) corrects `markLabel` —
  the switch's accessible name, asserted on by two component tests — and three sibling strings.
- `StartContratosMenoresImport` stays, and stays the single-family trigger's path. Nothing about a
  contratos-menores-only import changes.

**Out of scope:** the scheduler and R28's coverage of this family, R29's yielding, the UI copy
(TASK-0025), and any change to either family's walk.

## Acceptance criteria

- Marking an unmarked, active Órgano claims **one** run whose `importer` is `AMBAS_FAMILIAS` and
  whose coverage is **two** rows for that Órgano, one per family.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #4 immediate half, #38)
- Both families run, contratos menores first — asserted on the order the two executions are invoked
  in, not on wall-clock. (SPEC-0008 #4 immediate half)
- **A failure in the contratos menores half still runs the licitacións half**, and the run settles
  `PARTIALLY_SUCCEEDED` with one coverage row failed and one succeeded. (SPEC-0008 #38)
- Both halves failing settles `FAILED`; both succeeding settles `SUCCEEDED`. (SPEC-0008 #38)
- Marking while a run holds the guard refuses **as a whole**: the mark is still written, the `200`
  body carries the guard refusal, and **no** run row exists — neither family half-claimed.
  (SPEC-0008 #4 refusal half, #40 refusal half)
- **Marking an already-marked Órgano asks for the import again** — one run covering both families,
  or the guard refusal in the `200` body — exactly as today. This is documented behaviour, not an
  accident: `openapi.yaml` says the import *"is asked for every time, which is what lets an
  administrator whose mark was refused ask again without unmarking first"*, and with R28's scheduler
  deferred for this family it is the **only** recovery path a refused mark has. An earlier draft of
  this task asserted the opposite and would have removed it. (SPEC-0008 #4 refusal half)
- The mark endpoint's `summary` **and** `description` no longer say the mark is about contratos
  menores alone, the conformance run passes, and `scripts/openapi-lint.sh` passes.
- `claim` is still the only insertion path — one call, two coverage pairs.
  ([ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md))
- Integration-tested against a running application with both sources stubbed.
