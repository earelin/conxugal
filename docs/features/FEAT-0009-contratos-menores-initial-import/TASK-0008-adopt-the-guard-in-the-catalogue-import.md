---
feat: FEAT-0009
domain: backend
adrs: [0002, 0017]
status: done
depends_on: [TASK-0007]
---

# Adopt the guard in the catalogue import

Move `ImportOrganos` off its in-process `AtomicBoolean` onto
[TASK-0007](TASK-0007-import-run-record-and-guard.md)'s durable guard, recording a run row as
it goes. This is the half of R22 that **changes shipped behaviour**, which is why it is its own
task, on FEAT-0006's own build-then-adopt precedent.

R22's guard is system-wide across both importers, so the catalogue import has to *write* a live
run row — a guard cannot see an import that records nothing. The consequence is accepted rather
than discovered: FEAT-0006's daily overnight catalogue import will be **refused for the whole
duration of every multi-day initial import**.

## Scope
- `ImportOrganos` claims the guard instead of setting `running`; the `AtomicBoolean` and its
  `finally` release are deleted. `ImportOutcome.alreadyRunning()` now means *another import,
  of either importer, holds the guard* — a wider condition than before, and the reason the
  scheduler can now be refused by work it has never heard of.
- The run row is written with `importer = ORGANOS` on claim and completed with the reconciliation's
  verdict, `finished_at` and its added/refreshed counts. The catalogue import reports no
  per-Órgano outcome, so it writes no `import_run_organo` rows — SPEC-0007 R17 already
  anticipates an importer whose runs are not per-Órgano.
- **The `deactivated` count is not recorded, deliberately.** `ImportOutcome` keeps carrying it
  to the caller, which is what SPEC-0004 #10 asks and what FEAT-0007's import banner renders;
  the run record holds only the columns this feature's guard, resumer and R20 outcome read, and
  none of them reads it. A third count column would be justified by SPEC-0007 wanting it, which
  is the reasoning TASK-0007 rules out. SPEC-0007's feature adds it to the same row if it needs
  it.
- `last_advanced_at` is stamped on claim and on completion. The catalogue import is a single
  short transaction, so there is no mid-run progress to advance — and nothing here should invent
  one.
- **Recording never breaks the import** (ADR-0017, SPEC-0007 R20): a failed completion write is
  logged and abandoned, never propagated. It must not roll back a reconciliation that committed.
  The claim is the exception — a claim that fails *is* the refusal, and that is the point.
- Reshape the two existing tests: `ImportOrganosTest`'s already-running case (which today drives
  the in-process flag) and `ImportOrganosAtomicityIntegrationTest`. Stub the guard through the
  port with Mockito, per the project's repository-stubbing convention.
- `ImportOrganosScheduler` is untouched in code, but its behaviour changes: assert that a
  scheduled catalogue import arriving while any run is live returns `ALREADY_RUNNING` and
  reconciles nothing.

## Acceptance criteria
- A catalogue import triggered while **any** run is live — including one of the other importer
  — does not start, returns `ALREADY_RUNNING`, and writes nothing to the catalogue.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #32)
- A catalogue import triggered while no run is live starts, holds the guard for its duration,
  and releases it on completion — after which the next trigger starts normally. (SPEC-0005 #32)
- After a successful catalogue import, a run row exists carrying `ORGANOS`, `SUCCEEDED`, both
  timestamps and the added/refreshed counts; after a failed one, `FAILED`. The outcome returned
  to the caller still carries the deactivated count, which the row does not.
  (SPEC-0005 #29, run-record half; SPEC-0004 #10)
- A guard that was left `IN_PROGRESS` by a process that died stops blocking the catalogue import
  once the abandonment bound passes — the crash-then-restart case the `AtomicBoolean` used to
  handle for free. (SPEC-0005 #32)
- A failure to write the completion record leaves the reconciliation committed and the outcome
  reported as it was. ([SPEC-0007](../../specs/SPEC-0007-monitor-import-runs.md) R20)
- Every existing behavioural expectation of `ImportOrganos` — reconciliation, atomicity, source
  failure writing nothing — still holds.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #7, #12, #13)
