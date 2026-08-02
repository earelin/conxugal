---
feat: FEAT-0009
domain: backend
adrs: [0002, 0017]
status: todo
depends_on: [TASK-0007, TASK-0009]
---

# Multi-Órgano orchestration

`ImportContratosMenores`: the use case that turns [TASK-0009](TASK-0009-single-organo-initial-import.md)'s
single walk into a run — eligibility, serial execution, per-Órgano failure isolation, and the
run record that says what happened. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) and
[ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md).

Synchronous and blocking by design: it is a domain use case, and it runs for days.
[TASK-0011](TASK-0011-triggers-and-run-read.md) is what submits it to an executor and answers
the trigger immediately.

## Scope
- Two entry points, because a trigger must answer in milliseconds about a job that runs for
  days:
  - **claim** — takes the scope (every eligible Órgano, or one named Órgano), evaluates
    eligibility, claims the guard and writes the run row with its covered Órganos enumerated,
    then returns **the run identifier or a refusal**. Synchronous and short.
  - **execute** — takes that run identifier and performs the walks. Long.
- **Eligibility is `active && importado`, evaluated here** and not by each trigger, so the
  manual trigger, the mark trigger and the future scheduler cannot disagree about it. A named
  Órgano that fails the test yields the **not-eligible** refusal — a different refusal from the
  guard being held (R20, #34) — and starts no run.
- Órganos are processed **serially**, one finished before the next begins. R22's reason is
  reportability, not pacing: it gives the per-Órgano outcomes a well-defined order, so at any
  moment a run is working on one identifiable Órgano.
- Each Órgano is dispatched by the **mode rule**, not by the trigger that arrived:
  `INITIAL` and `RESUMED` run TASK-0009's walk; `INCREMENTAL` is **skipped with that reason
  recorded** on its per-Órgano row — this feature does not implement it, and reporting it as
  either success or failure would be a lie.
- **Per-Órgano failure isolation (R23):** a source failure aborts that Órgano, marks its row
  `FAILED` with the reason, and the run **carries on to the next**. Contracts already stored
  for it and for Órganos processed earlier stay intact.
- **The unmark stop (R5, #8):** eligibility is re-checked at the batch boundary TASK-0009's
  walk already has — **which means changing that walk's signature to take the check**, since
  TASK-0009 deliberately builds no extension point for a caller that did not yet exist. The
  boundary is there; the way of hanging something off it is this task's to add. An Órgano unmarked mid-run stops **cleanly at that boundary**, keeping
  everything stored and leaving the cursor where it is; its row is `STOPPED`, and its state
  stays `INCOMPLETE`, which is what makes a later re-mark resume rather than restart.
- **The run's verdict** is derived from the per-Órgano rows when the run ends, **over the failed
  rows, not the successful ones**: no row failed → `SUCCEEDED`; every row failed →
  `FAILED`; some failed and some did not → `PARTIALLY_SUCCEEDED`. A run whose Órganos were all
  `SKIPPED` (every one already `COMPLETE` — the ordinary case once the catalogue is loaded) or
  all `STOPPED` (unmarked mid-run) therefore reads **`SUCCEEDED`**, because nothing failed. A
  rule phrased as *none succeeded → failed* would report those runs as failures, which is
  exactly the lie TASK-0007 gives those two states to avoid. Partial success is a first-class
  verdict, not an edge case — R23 requires a run to carry on past a failing Órgano, which makes
  it the likeliest verdict of a multi-Órgano run. Counts are accumulated per Órgano and totalled
  on the run.
- **No check for an Órgano becoming inactive mid-run.** R5 requires the run to stop for it and
  it cannot happen: only the catalogue import deactivates an Órgano, and the system-wide guard
  forbids it running while this one does. The obligation is met by the guard rather than by a
  check — worth knowing before someone writes the check.

## Acceptance criteria
- A run scoped to all marked Órganos covers exactly those that are **active and marked**; an
  active-but-unmarked and a marked-but-inactive Órgano each have nothing stored from it.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #3)
- The covered Órganos are enumerated on the run record at claim time, before any is touched, and
  a run that dies part-way still names all of them. (SPEC-0005 #29, covered-Órganos half)
- Órganos are imported one at a time: no two are in progress simultaneously. (SPEC-0005 #32,
  serial half)
- A run where one Órgano's source fails ends `PARTIALLY_SUCCEEDED`, names the failing Órgano,
  retains the contracts imported for Órganos processed earlier, and still imports the remaining
  ones. (SPEC-0005 #30, #36)
- A run where every covered Órgano fails ends `FAILED`; one where all succeed ends `SUCCEEDED`;
  and one in which every covered Órgano was skipped or stopped — nothing failed and nothing was
  imported — also ends `SUCCEEDED`, not `FAILED`. (SPEC-0005 #29, initial/resumed modes only)
- An Órgano that already holds contracts and is then **unmarked** keeps every one of them, and
  a subsequent run retrieves nothing further for it — it is simply not among the covered
  Órganos. (SPEC-0005 #7, first two clauses; the *says it is no longer being updated* clause is
  the browsing feature's)
- Unmarking an Órgano whose import is in progress stops it at the next batch boundary without
  losing a contract already stored, leaves it `INCOMPLETE`, and a later re-mark **resumes** it
  rather than restarting. (SPEC-0005 #8, #46)
- An Órgano whose initial import has completed is skipped with the incremental mode named as the
  reason — neither reported as imported nor as failed. (SPEC-0005 #29, initial/resumed modes
  only)
- A single-Órgano scope naming an unmarked or inactive Órgano starts no run and reports
  **not eligible**, distinguishably from the guard refusal. (SPEC-0005 #34)
- Unit-tested with the ports stubbed (Mockito) — no database or HTTP — for eligibility,
  ordering, isolation, the unmark stop and each verdict; the run record's contents are
  integration-tested against PostgreSQL.
