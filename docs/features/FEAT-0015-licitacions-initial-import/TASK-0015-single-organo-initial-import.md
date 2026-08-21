---
feat: FEAT-0015
domain: backend
adrs: [0002, 0011, 0014, 0017]
status: todo
depends_on: [TASK-0001, TASK-0002, TASK-0007, TASK-0008, TASK-0014]
---

# A single Órgano's initial import

`ImportOrganoLicitacions` — the walk: one Órgano's full published tender history, page by page,
**one record retrieved per listing entry**, stored idempotently and resumable from where it stopped.
One Órgano only; enumerating them and recording the run are
[TASK-0016](TASK-0016-multi-organo-orchestration-and-failure-isolation.md)'s, and the
outstanding-record ledger is
[TASK-0023](TASK-0023-the-outstanding-record-ledger-in-the-walk.md)'s.

Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (orchestration over
ports, no transport or SQL of its own) and
[ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md).

**The scale is what makes this different from its contratos menores sibling.** SERGAS is 16 798
procedures, one record each — median 138 KB, mean 168 KB, so ~2.7 GB and **~4.7 hours** at a
courteous rate. Nothing here makes that cheaper; what it does is make it **wasted at most once**.

## Scope

- **The walk**: `id` **ascending**, paged at **100**, one record fetch per entry, each reconciled
  through [TASK-0014](TASK-0014-reconciling-a-restated-procedure.md)'s `StoreLicitacion` — which
  takes the listing entry alongside the record, so the walk passes both.

  **Ascending by `id`, not by date.** An initial import needs an order that is **stable under
  concurrent publication**: a procedure published mid-walk takes a higher identifier and appends at
  the end, so pages already read do not shift beneath the walk. Ordered by publication or
  modification date, a single edit reshuffles the history and offset paging silently skips rows.
- **On first start the walk inserts the state row at `INCOMPLETE`**, before its first page. Without
  it a run interrupted before completion leaves no row at all, which reads as `NEVER_STARTED` and
  restarts the Órgano from offset 0 — silently failing the resumption this task promises. **On
  resumption** it continues from the stored cursor and never re-creates the row.
- **Termination: the walk ends when the listing is exhausted** — a page returns fewer entries than
  it asked for, or the offset passes `recordsTotal`, **re-read on every response** because it moves
  while a multi-hour import runs.

  **Not when a stored count matches `recordsTotal`.** That alternative cannot terminate: one
  permanently unparseable record out of 16 798 leaves the count short for ever, and the Órgano
  re-walks a history it has already read on every subsequent run. FEAT-0009 met the same shape and
  answered it with a configured history floor; this family has a better answer, because its listing
  has an end.
- **The cursor is the offset already consumed**, written after each page's procedures commit, in its
  own short transaction (ADR-0017). A crash between the two leaves the cursor slightly behind what
  is stored, and the resumption re-reads the overlap harmlessly.
- **A resumption steps back one page** rather than trusting the offset exactly, on the same
  reasoning FEAT-0009 applied to its window boundary: the stability argument above is sound but
  unproven.

  **What the step-back costs is stated honestly, because it is not one request**: re-reading a page
  means **100 record fetches — about 13.8 MB at the median**. Accepted for a walk of thousands, and
  it is the price of not trusting an unmeasured property. The cheap fix — skip an entry whose stored
  last-modified equals the listing's `modificado` — is deliberately **not** built here: it is the
  incremental feature's mechanism, and building half of it early is how two walks end up disagreeing
  about what *unchanged* means.
- **The guard re-check, applied rather than invented.** ADR-0017 warns that a live run which stops
  advancing loses the guard and the next trigger claims — "and if the first one then wakes and
  advances, both are live and both are reading the source." **That remedy ships**:
  `ReadContratosMenoresWindow` asks `importRuns.holdsGuard` **twice per page**, and this walk copies
  both asks and their positions rather than paraphrasing them:
  - **before fetching the page**, so a run already dead issues nothing;
  - **after the page's procedures commit and before the progress write** — the load-bearing one,
    because the progress write renews the run's own last-advanced stamp, so a walk that asked only
    at the top "would be reading a liveness it had just written itself."

  **What this family must decide is the granularity**, and it decides it here: the guard is also
  re-checked **every 10 records within a page**. A contratos menores page is one request; a
  licitacións page is ~101 requests over roughly 100 seconds, so a page-boundary-only check would
  let a walk that has lost the guard issue up to 99 more record fetches — ~13.7 MB — against a source
  another import is by then also reading. Ten records bounds that at ~1.4 MB for one extra
  cheap query per ten fetches.
- **The run's progress is advanced after every page**, so a walk that is working is never read as
  abandoned. A failed progress write is logged and abandoned — the import wins, and it must never
  roll back committed procedures.
- **Two clean stops, reported distinguishably.** The Órgano **unmarked** mid-run stops at a page
  boundary, keeping everything stored, the cursor where it is and the state `INCOMPLETE`. **Guard
  lost** stops likewise but is a different answer, because
  [TASK-0016](TASK-0016-multi-organo-orchestration-and-failure-isolation.md) must settle nothing on
  a run another import now owns. On the shipped `StopReason` precedent.

**Out of scope:** the outstanding ledger and the `COMPLETE` gate that reads it (TASK-0023 — until it
lands, an exhausted listing completes the Órgano), the incremental mode, R29's yielding (this walk
**holds the guard to completion**), multi-Órgano orchestration, and any endpoint.

## Acceptance criteria

- An Órgano with a full history is walked to the end of its listing, and the stored count equals the
  source's `recordsTotal`. *This is #7's other half: after an initial import completes, every
  licitación the source publishes for that Órgano is stored.*
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #7 completeness half)
- **A walk beginning on an Órgano with no state row inserts one at `INCOMPLETE` before its first
  page**, and a walk interrupted after that resumes rather than restarting. (SPEC-0008 #12)
- Interrupting the walk part-way retains every procedure already stored; resuming continues **from
  the cursor**, reads fewer pages than a restart would, and completes with **no duplicates**.
  (SPEC-0008 #12, #17)
- **A resumption from cursor offset N issues its first listing request at offset `max(N − 100, 0)`**,
  and the up-to-100 procedures re-read are refreshed in place, adding no duplicates. (SPEC-0008 #17)
- A crash simulated **between a page's commit and its cursor write** leaves the cursor behind what
  is stored; the resumption re-reads the overlap and the stored set is unchanged by it.
  (SPEC-0008 #12, #17)
- `recordsTotal` growing mid-walk extends the walk rather than ending it early or leaving it
  looping. (SPEC-0008 #12)
- An Órgano answering `recordsTotal: 0` completes after a **single** listing request, with no record
  fetches and no procedures stored. It still advances its run once, so a covered Órgano that did
  nothing is not left looking `PENDING`. (SPEC-0008 #11)
- **The run's progress is advanced once per page** — asserted by counting `advance` calls against
  pages.
- **A walk whose run has ceased to hold the guard stops within 10 record fetches** — asserted on
  the calls **both** source ports receive, since the whole point is the requests *not* made.
  ([ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md))
- **Guard loss and an unmark are reported distinguishably**, and both leave the cursor and every
  stored procedure exactly as found. (SPEC-0008 #6 unmarking half)
- **Deleting every run record for the Órgano does not affect a resumption**: the cursor lives with
  the Órgano, which is what stops SPEC-0007 R17's pruning stranding a half-loaded one.
  (SPEC-0008 #12)
- A failed progress write leaves the committed procedures committed and the walk running.
  (SPEC-0008 #12)
- A source failure mid-walk leaves the procedures already stored intact and the cursor usable.
  (SPEC-0008 #41)
- Unit-tested with the two source ports and the repositories stubbed (Mockito) — no database, no
  HTTP — with the page sequence, the advance count and the guard re-checks asserted on the calls the
  ports receive. The resumption and crash-overlap cases are integration-tested against PostgreSQL
  and a stubbed source.
