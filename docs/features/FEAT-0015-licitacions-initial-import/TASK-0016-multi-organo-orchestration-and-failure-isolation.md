---
feat: FEAT-0015
domain: backend
adrs: [0002, 0017]
status: todo
depends_on: [TASK-0001, TASK-0015]
---

# Multi-Órgano orchestration and failure isolation

Who a licitacións run covers, in what order it takes them, and what the run amounts to when it is
over. `ClaimLicitacionsImport` and `ExecuteLicitacionsImport`, on the shape
`ClaimContratosMenoresImport` and `ExecuteContratosMenoresImport` already have — with the run
recorded per family, which [TASK-0001](TASK-0001-per-family-run-coverage.md) made possible.

R30's isolation is **two-level**, and only the outer level is new here: a **record** failing neither
its Órgano nor its run is
[TASK-0023](TASK-0023-the-outstanding-record-ledger-in-the-walk.md)'s, and an **Órgano** failing
neither its siblings nor the run is this task's.

## Scope

- **`ClaimLicitacionsImport`** — `claimAll()` over every **active and marked** Órgano, and
  `claimOrgano(id)` over one, each claiming a run with `Importer.LICITACIONS` and coverage pairs of
  `(Órgano, ContractFamily.LICITACIONS)`.

  The three refusals stay distinct and travel as they arrive: `ImportAlreadyRunningException` when
  the guard is held, `OrganoNotEligibleForImportException` when the named Órgano is unmarked or
  inactive, `OrganoNotFoundException` when it is not there at all. A refused claim **writes
  nothing**.
- **`ExecuteLicitacionsImport`** — the covered Órganos read back from the run and filtered to
  **this family's** coverage rows, processed **serially**, because the guard admits one import at a
  time and the R31 rate budget is one budget.
- **The mode is decided per Órgano from its own state**, through
  [TASK-0002](TASK-0002-licitacions-per-organo-import-state.md)'s `LicitacionImportMode.of(status)`
  — never from the trigger that arrived, which is how a half-loaded Órgano ends up restarted or
  skipped. An Órgano answering `INCREMENTAL` is **skipped with that reason recorded**, neither done
  nor failed. *That skip is the placeholder for #11's incremental clause, which this feature does
  not claim; what it proves is the initial and resumed branches.*
- **The verdict rule is the shipped one**, read off the **failed** count rather than invented:
  `SUCCEEDED` when nothing failed, `FAILED` when everything did, `PARTIALLY_SUCCEEDED` otherwise. So
  a run in which every covered Órgano was **skipped or stopped** — nothing failed and nothing
  imported — ends `SUCCEEDED`, which is the ordinary outcome for a fully loaded catalogue and the
  case worth a test.
- **An Órgano left `INCOMPLETE` by outstanding records is recorded `SUCCEEDED` *with a reason*.**
  Its walk did what it was asked to do, so it is not a failure — but a bare `SUCCEEDED` is wrong
  too. The shipped analogue is exact: `ImportCoveredOrgano.Settlement.reachedTheHistoryFloor()` is
  `SUCCEEDED` **with** `REACHED_THE_HISTORY_FLOOR`, because *"without a reason it would read on the
  row exactly like one that finished, on this run and on every run after it."* This task follows it
  with a reason naming how many records are outstanding.

  An earlier draft said the incompleteness was visible "in the per-Órgano import state and nowhere
  else", which read R30 backwards and sat badly with SPEC-0007 R9/#19 — the record is supposed to
  name which publication a failure concerned.
- **Per-Órgano failure isolation**: one Órgano's failure is recorded on its own coverage row with
  its reason, and the walk moves to the next.
- **The guard-lost contract.** A walk that stops because its run no longer holds the guard is not
  an outcome to record: the run has been claimed by whoever triggered after it went quiet, and **its
  record is theirs**. So the orchestrator stops walking the Órganos after it, writes no further
  coverage row and **settles no verdict** — exactly what `ExecuteContratosMenoresImport` does today.
  Writing a verdict onto a run another import now owns is the corruption ADR-0017's warning exists
  to prevent.
- The unmarked-mid-run stop is recorded as **`STOPPED`**, which the published contract already
  documents as not a failure.
- **The counts are per Órgano per family**, written by `advance` and settled by `finishOrgano`
  against `(run, organo, LICITACIONS)`.

**Out of scope:** the endpoints ([TASK-0017](TASK-0017-the-licitacions-triggers.md)), the
both-family use case ([TASK-0018](TASK-0018-start-marked-organo-import.md)), the scheduler, and
R29's yielding.

## Acceptance criteria

- A run over three Órganos where the second fails records **three** coverage rows — succeeded,
  failed with a reason, succeeded — and settles `PARTIALLY_SUCCEEDED`. The third Órgano is walked,
  which is the isolation this asserts.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #3, #41)
- A run in which every covered Órgano was **skipped or stopped** — nothing failed, nothing imported
  — settles **`SUCCEEDED`**, not `FAILED`. (SPEC-0008 #38)
- Every coverage row a licitacións run writes carries `family = LICITACIONS`, and a concurrent
  contratos menores history for the same Órgano is untouched by it. (SPEC-0008 #38)
- **A run over an Órgano with N new and M restated procedures records `added = N, refreshed = M` on
  its `LICITACIONS` coverage row, and nothing on its `CONTRATOS_MENORES` one.** (SPEC-0008 #38)
- An Órgano whose walk ended `INCOMPLETE` because a record is outstanding is recorded **`SUCCEEDED`
  with a reason naming the outstanding count** — distinguishable on the row from one that finished
  clean. (SPEC-0008 #41)
- **A run that loses the guard mid-walk stops walking the Órganos after it, writes no further
  coverage row and settles no verdict** — its record is left as the live run's.
  ([ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md))
- An Órgano at `COMPLETE` is **skipped** with the reason recorded, and the run does not count it as
  a failure. *(The placeholder for #11's incremental clause, not a claim on it.)*
- An Órgano at `NEVER_STARTED` takes the initial mode and one at `INCOMPLETE` the resumed mode,
  decided from the Órgano's state and not from which trigger claimed the run.
  (SPEC-0008 #5 run half, #11 initial-and-resumed modes only)
- `claimAll` covers exactly the active **and** marked Órganos; an inactive one and an unmarked one
  are both absent from the coverage. (SPEC-0008 #3)
- `claimOrgano` against an unmarked Órgano raises ineligibility and **claims no run** — the guard is
  not taken and no row is written. (SPEC-0008 #3)
- A claim made while another import holds the guard raises `ImportAlreadyRunningException` and
  writes nothing. (SPEC-0008 #40 refusal half)
- Unit-tested with the ports stubbed (Mockito), and the verdict-aggregation, count and
  coverage-filtering cases integration-tested against PostgreSQL.
