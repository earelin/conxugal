---
feat: FEAT-0009
domain: backend
adrs: [0002, 0011, 0014, 0017]
status: done
depends_on: [TASK-0004, TASK-0005, TASK-0006, TASK-0007]
---

# A single Órgano's initial import

The walk: one Órgano's full published history, retrieved newest-first in three-month windows,
stored idempotently, and resumable from where it stopped. One Órgano only — enumerating them,
isolating their failures and recording the run are
[TASK-0010](TASK-0010-multi-organo-orchestration.md)'s. Governed by
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) (orchestration over ports, no
transport or SQL of its own) and
[ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md) (durable resumption
state, advanced outside the data transaction).

## Scope
- The use case walking one Órgano: from today backwards, **three-month windows, newest first**,
  paging each window at **100 rows** to exhaustion before stepping back. Window and page size
  are the source's measured limits ([`design/source-contract.md`](design/source-contract.md)),
  honoured by construction and not configuration —
  [TASK-0007](TASK-0007-import-run-record-and-guard.md) ties the progress batch to exactly this
  page, so one page read, one page upserted and one progress advance are the same beat.
- Newest-first is a decision with reasons, not an arbitrary direction: an initial import of a
  large Órgano runs for days, so the most-consulted contracts become browsable within hours
  rather than at the end, R18's *partial* marker describes a list growing backwards rather than
  one missing everything recent, and R19's default year is meaningful from the first batch.
- **After each batch, in this order:** upsert the batch (one transaction), then advance the
  cursor and the run's progress **in their own short transaction** (ADR-0017). A failed
  progress write is logged and abandoned — the import wins and the record is what is sacrificed;
  it must never roll back committed contracts.
- **The cursor is a conservative hint, not a ledger.** A crash between a data commit and its
  cursor write leaves it slightly behind what is stored, and the resumption re-reads that
  overlap harmlessly, because R11 and R12 make re-reading an update in place. That is the trade
  ADR-0017 takes deliberately; do not close it by making the two writes atomic.
- **On first start:** create the state row at `INCOMPLETE` and stamp `covered_through` (T₀).
  **On resumption:** continue from the stored cursor, never from today, and never re-stamp T₀.
- **Termination.** The walk ends when the Órgano's **stored count reaches the source's
  `recordsTotal`**, and reaching it is what marks the state `COMPLETE`. Every response carries
  that figure, so completeness is checked **against the source** rather than inferred.
  - It does **not** stop at the first empty window. For the small Órganos that are most of the
    catalogue a quarter with no contratos menores is ordinary; stopping there would mark an
    Órgano complete with most of its history unread and leave it thereafter on the incremental
    path — failing #12 invisibly.
  - `recordsTotal` is **live**: it grows while a multi-day import runs, so it is re-read on
    every response and used as a **test evaluated when the walk believes it is done**, never as
    a fixed target to subtract from.
  - A **configured history floor** (the source's published history begins around 2018) is the
    backstop so a walk cannot run backwards forever if the two never converge. A walk that
    reaches the floor without matching the count ends **`INCOMPLETE`**, not silently complete,
    so it is resumed rather than quietly treated as loaded.
- An Órgano answering `recordsTotal: 0` — the majority of the catalogue — completes after a
  single request rather than walking years of empty windows. That is a completed import, not a
  failure.
- A source failure aborts **this Órgano's** walk with everything it stored intact and the cursor
  where it stands; propagating it is the caller's business, not this use case's.
- The batch loop has an explicit per-batch boundary — the point at which the cursor is
  advanced — because TASK-0010's unmark check hangs off it. Do not build a callback abstraction
  for it here; there is nothing yet to pass one.

## Acceptance criteria
- An Órgano with several years of publications is imported back to its **earliest** publication
  — not merely the last few years — with the stored count equal to the source's `recordsTotal`
  and the state left `COMPLETE`.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #12)
- A window with no publications in the middle of the history does not end the walk; the earlier
  windows beyond it are still read. (SPEC-0005 #12)
- Interrupting the walk part-way retains every contract already stored, and resuming continues
  **from the cursor** — reading fewer windows than a restart would — and completes the full
  history with **no duplicates**. (SPEC-0005 #14 retained-and-resumed-on-demand halves, #17)
- A crash simulated **between a data commit and its cursor write** leaves the cursor behind what
  is stored; the resumption re-reads the overlap and the stored set is unchanged by it.
  (SPEC-0005 #14 retained half, #17)
- **Deleting every run record for the Órgano does not affect a resumption**: the walk continues
  from the cursor exactly as it would have, because the cursor and `covered_through` live with
  the Órgano. This is what stops SPEC-0007 R17's pruning stranding a half-loaded Órgano — the
  schema property TASK-0006 establishes, exercised here where there is a resumption to run.
  (SPEC-0005 #14 retained-and-resumed-on-demand halves)
- Running the whole import twice over the same published contracts leaves the stored set and
  every attribute unchanged, and the second run reports nothing added. (SPEC-0005 #17)
- A contract whose published attributes changed at the source is refreshed **in place** on the
  second run: same identity, same row, new values. (SPEC-0005 #16, storage half)
- `recordsTotal` growing mid-walk does not end the walk early and does not leave it looping: the
  walk continues and completes against the figure current when it finishes. (SPEC-0005 #12)
- A walk that reaches the configured floor without matching `recordsTotal` leaves the Órgano
  `INCOMPLETE`. (SPEC-0005 #12, #46)
- An Órgano whose `recordsTotal` is 0 completes with one request and no contracts stored.
  (SPEC-0005 #26, storage half)
- A source failure mid-walk leaves the contracts already stored intact and the cursor usable for
  a later resumption. (SPEC-0005 #36)
- Unit-tested with the source and repository ports stubbed (Mockito, per the project's
  convention) — no database or HTTP — with the walk's window sequence asserted on the calls the
  port receives; the resumption and crash-overlap cases are integration-tested against
  PostgreSQL and a stubbed source.
