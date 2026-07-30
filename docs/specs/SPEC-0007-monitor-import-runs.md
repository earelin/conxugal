---
status: draft
---

# SPEC-0007. Monitor import runs

## Summary

Everything the system imports, it imports in the background: the Órgano de Contratación
catalogue of [SPEC-0004](SPEC-0004-import-manage-organos-contratacion.md), and the contratos
menores of [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) in each of its modes —
an initial load of an Órgano's full history, the scheduled incremental runs that follow, and
an administrator's historical re-read. This spec makes those runs **observable**: each one
leaves a **durable record** an administrator can inspect afterwards, a run still in flight
reports **how far it has got**, and the administration area offers a **page** where an
administrator reviews outcomes and diagnoses failures.

The capability exists because today a run's outcome survives only as the response to the
request that triggered it. That is workable for a manual trigger and useless for everything
else. A scheduled run has no requester: SPEC-0004 R11 and SPEC-0005 R20 both promise
automatic imports, and neither leaves anything an administrator can look at afterwards. An
initial import of a large Órgano runs for hours and may be interrupted and resumed several
times before it completes, so "did it work?" is not even a question with a single answer. And
SPEC-0004 R13 and SPEC-0005 R22 each say a failure is *"reported to the administrator (for a
manual run) **or otherwise recorded**"* — a clause with no requirement behind it and no
acceptance criterion to prove. This spec is what "otherwise recorded" means, and it is what
discharges SPEC-0005 R9's promise that *"while it is in progress an administrator can see
that it is running and how far it has got"*.

Monitoring is specified separately from any one importer because **the importers are plural
and growing**. There are two today and licitacións will be the third, all triggered the same
way, all failing the same ways, all needing the same page. Putting the history inside
SPEC-0005 would make SPEC-0004's catalogue import depend on the spec that consumes it, and
would leave the next family duplicating it. So this spec is **importer-neutral**: it owns the
record and the review surface, and each importer's spec keeps owning what it imports and
when.

The page lives in the administration area of
[SPEC-0003](SPEC-0003-administration-area.md) and follows its access rule — administrators
only. It is not that spec's detailed-metrics view and must not be confused with it: those
metrics are transient by requirement (SPEC-0003 R20 forbids the backend storing them), while
import history is durable by definition. What the two share is an expectation of liveness,
and there is prior art for it in
[ADR-0009](../architecture/0009-sse-admin-realtime-metrics.md).

## Scope

Deliberately **out of scope**:

- **Triggering, scheduling and performing imports.** Owned by SPEC-0004 (R10, R11) and
  SPEC-0005 (R8, R19, R20), including resuming an interrupted initial import (SPEC-0005 R9)
  and requesting a historical re-read (SPEC-0005 R10). This spec **observes**; it never
  starts, stops or re-runs anything. A run's existence, its mode and its lifecycle are facts
  it records, not decisions it makes.
- **What an import does to the data.** Reconciliation, identity, idempotency and retention of
  imported records belong to the importing specs.
- **Transient runtime metrics.** SPEC-0003 R17–R21 own the live JVM/datastore metrics view,
  which the backend must not persist. Import history is the opposite kind of data and does not
  belong there.
- **User-facing import status.** SPEC-0005 R17 deliberately makes three empty-list cases
  distinguishable *to a `USER`* — not imported, initial import unfinished, imported and empty.
  That surface stays SPEC-0005's. This spec adds no `USER` surface at all (R1).
- **Alerting.** Notifying an administrator out-of-band that a run failed — email, push, a
  webhook — is a separate capability. This spec makes a failure discoverable when an
  administrator looks; it does not go and tell them.
- **A general application log viewer.** The records here describe import runs, not the
  application's log stream.
- **Exporting run history.** Left with the export capability SPEC-0001 promises and
  SPEC-0005 and SPEC-0006 both defer.

### Decisions this spec leaves open

1. **How live progress reaches the browser.** R6 fixes the obligation — no manual refresh —
   and not the transport. [ADR-0009](../architecture/0009-sse-admin-realtime-metrics.md)
   already chose Server-Sent Events for admin live data and would be the natural precedent,
   but whether this capability reuses it is a feature-level decision and should be recorded if
   it diverges.
2. **Where a run's progress and resumption point are held.** SPEC-0005 already lists "how a
   long-running, resumable import job holds its state" as an open, ADR-grade decision. R5 and
   R7 here describe what an administrator must be able to *see*; they must not be read as
   binding that state to this spec's records, nor as requiring two separate stores. Whichever
   ADR settles the job state should settle this with it.
3. **How retention is enforced.** R16 fixes the bound and the exception to it, not the
   mechanism or the cadence.

## Requirements

### Access

- **R1** — Every function in this spec — the run list (R11), its filters (R12), a run's detail
  and diagnostics (R13), and live progress (R6) — is reachable only by users with the `ADMIN`
  role. A `USER` or an unauthenticated visitor who requests any of them is denied (consistent
  with SPEC-0003 R1). There is **no `USER`-facing surface**: import history is operational
  data about the system, not information about contracts.

### Recording every run

- **R2** — **Every** import run is recorded, whichever importer performs it and whatever
  triggers it: the catalogue import of SPEC-0004, and each contratos menores mode of
  SPEC-0005 — initial import, scheduled or on-demand incremental import, automatic or
  administrator-requested resumption of an incomplete initial import, and historical
  re-read. Recording is not conditional on a human being present to receive the outcome: the
  automatic runs of SPEC-0004 R11 and SPEC-0005 R20 are precisely the ones with no other
  witness. This requirement is what SPEC-0004 R13 and SPEC-0005 R22 mean by *"or otherwise
  recorded"*.
- **R3** — Each record states, for the run it describes: **what was imported** — which
  importer, and for a per-Órgano import which Órgano; the **mode** it ran in; **what triggered
  it**, distinguishing an administrator from the scheduler from an automatic resumption, and
  naming the administrator for a manual trigger; **when it started** and **when it ended**;
  its **terminal state** (R4); and the **counts the importer reports** — for contratos
  menores, contracts added and refreshed (SPEC-0005 R19); for the catalogue, Órganos added,
  refreshed and marked inactive (SPEC-0004 R10).
- **R4** — A run's terminal state distinguishes, at minimum:
  - **succeeded** — it completed and did what it set out to do;
  - **failed** — it could not;
  - **partially succeeded** — a run spanning several Órganos where some failed and others did
    not. This is not an edge case: SPEC-0005 R22 *requires* a run to carry on past a failing
    Órgano, so a multi-Órgano run is more likely to end here than in either of the above;
  - **stopped** — ended deliberately rather than by failure, as when unmarking an Órgano halts
    an import in progress (SPEC-0005 R5);
  - **refused** — not started because a run for that Órgano, or that importer, was already in
    progress (SPEC-0005 R21, SPEC-0004 R12). A refused trigger is recorded rather than
    silently dropped, because a trigger that did nothing must not be indistinguishable from
    one that ran.

  A run currently executing is **in progress**, which is not a terminal state.
- **R5** — While a run is in progress its record reports **progress**: that it is running, a
  measure of how far it has got that only ever advances, and **when it last advanced**. For an
  initial import of over a million contracts, "running" is not progress — an administrator's
  actual question is whether it is advancing or stalled, and only the last two of those three
  answer it.
- **R6** — Progress and a run's transition to a terminal state reach a watching administrator
  **live**, without them manually refreshing — the same expectation SPEC-0003 R18 sets for
  detailed metrics. The transport is decided outside this spec.
- **R7** — For a run that can be resumed (SPEC-0005 R9), the record states **how much was
  already stored** and **the point reached**, and successive attempts at one Órgano's initial
  import are relatable to one another, so an administrator can see that a resumption continued
  the load rather than restarted it, and can see an Órgano converging over several attempts
  instead of a sequence of unrelated failures.
- **R8** — A run whose process dies without ever recording an end is **not left indefinitely
  reported as in progress**. An abandoned run becomes distinguishable from one genuinely still
  executing — otherwise the single most important thing the page shows, what is running now,
  is the thing it is least able to be trusted about.

### Diagnosing failures

- **R9** — A failed or partially succeeded record carries enough detail to diagnose the
  failure **without reading server logs**: which stage of the run failed, what the source did
  — unreachable, refused the request, or returned a response the system could not use,
  together with the status or the respect in which it was unusable — and the underlying error.
  This is the requirement the capability exists for; a record that says only "failed" moves
  the debugging problem rather than solving it.
- **R10** — For a run covering several Órganos, the record states the **outcome per Órgano**,
  not only the aggregate: which succeeded, which failed, and why each failure failed. SPEC-0005
  R22 guarantees the run continues past a failing Órgano, so an aggregate verdict alone hides
  the only thing an administrator needs — *which* Órgano needs attention.
- **R11** — **No secret ever appears in a record.** No credential, token, key or connection
  password appears in any recorded error, message, diagnostic or identifier, whatever the
  underlying failure was (the same rule as SPEC-0003 R5 and R21). This constraint is what makes
  R9's technical detail safe to retain and to display.

### Reviewing runs

- **R12** — An administrator can open a page listing recorded runs, **most recent first**,
  showing for each what was imported, its mode, what triggered it, when it ran, how long it
  took, its terminal state and its counts (R3). Runs **in progress** are presented distinctly
  from finished ones: they are the operationally urgent rows, and sorting alone does not make
  them stand out.
- **R13** — The list can be **narrowed** — by what was imported, by Órgano, by mode, by
  terminal state, and by when the run happened — and is **paginated**: an administrator sees
  one page at a time, is told how many runs the current selection contains and how many pages
  it spans, and can move between pages. History accumulates at the scheduler's cadence
  multiplied by the number of marked Órganos, so an unfiltered, unpaginated list stops being
  usable long before it stops being correct.
- **R14** — An administrator can open a **single run** and see everything R3 records about it,
  together with R5's progress if it is still running, R7's resumption detail if it is
  resumable, and R9's and R10's diagnostics if it did not fully succeed.
- **R15** — History is navigable **from the Órgano as well as from the run**: from a run of a
  per-Órgano import an administrator reaches the Órgano it covered, and from an Órgano they
  reach that Órgano's own run history — including **when it was last imported successfully**,
  and whether its initial import has completed (a fact SPEC-0005 R8 already requires the
  system to hold, and which nothing currently shows). *Is this Órgano actually up to date?* is
  the question the capability is most often opened to answer, and it is asked about an Órgano,
  not about a run.
- **R16** — An Órgano with **no retained runs** is not presented as one that was **never
  imported**. Retention is bounded (R17), so those two states converge over time unless they
  are kept apart deliberately.

### Retention and integrity

- **R17** — Run history is retained for a **bounded** period or count rather than
  indefinitely, because its volume grows without limit with the scheduler's cadence. The bound
  has one exception: for each Órgano and each importer, the **most recent successful run is
  always retained** regardless of age, so R15's *"last imported successfully"* never becomes
  unanswerable merely because history was pruned. Pruning is automatic; the bound is
  configurable and is not fixed here.
- **R18** — Pruning run history **never touches imported data**: no contract, Órgano, taxonomy
  placement, import mark, or record of a completed initial import is altered or lost when a run
  record is discarded. Monitoring data is derived from the act of importing and is disposable;
  what was imported is not.
- **R19** — Run records are **not editable**. No function amends or deletes an individual run
  — pruning under R17 is automatic and wholesale, never selective. A debugging trail that can
  be selectively rewritten is not a trail, and an operational record that can be tidied cannot
  be used to establish what happened.

### Non-functional expectations

- **R20** — **Recording never breaks the import.** A failure to record a run's start, its
  progress or its outcome does not fail, abort or alter an import that would otherwise have
  succeeded, and never causes an imported record to be lost, duplicated or left inconsistent.
  Observability is subordinate to the thing it observes; the failure mode where monitoring
  takes down the import it was added to protect is the one to design out.
- **R21** — **Recording is cheap.** An import of millions of contracts is not materially
  slowed by being observed: progress is reported at a granularity coarse enough to keep it so,
  rather than per contract. R5 requires progress that advances and is timely, which a coarse
  granularity satisfies.
- **R22** — The monitoring page stays responsive at the volume history reaches. Measured on
  the deployment's target environment, with at least **100 000** retained run records: the run
  list returns its first page and its count within **1 second at the 95th percentile**, and a
  page deep into that history meets the same budget as the first — paging must not degrade with
  depth, which is the failure mode this volume invites.

## Acceptance criteria

1. **(R1)** A `USER` or an unauthenticated visitor that requests the run list, a run's detail,
   or live progress is denied; an authenticated `ADMIN` is allowed. No `USER`-facing screen
   exposes import run history.
2. **(R2)** After a **scheduled** import runs with no human trigger, a record of it exists and
   is visible to an administrator afterwards. *(This is the case that has no other witness, and
   what SPEC-0004 R13 and SPEC-0005 R22 mean by "otherwise recorded".)*
3. **(R2)** A record exists for each of: the catalogue import, an Órgano's initial import, an
   incremental import, a resumption of an incomplete initial import, and a historical re-read.
4. **(R3)** A run's record states what was imported, its mode, its trigger, its start and end,
   its terminal state, and its counts; for a manually triggered run it names the administrator
   who triggered it, and for a scheduled run it identifies the trigger as the scheduler.
5. **(R4)** A run that completed, one that failed outright, one that covered several Órganos of
   which some failed, one halted by unmarking its Órgano, and one refused because an import of
   that Órgano was already running are each recorded in a **distinguishable** state; a run
   still executing is recorded as in progress and not as any terminal state.
6. **(R4)** Triggering an import of an Órgano already being imported produces a record of a
   **refused** run, rather than no record at all.
7. **(R5)** While an initial import runs, its record reports that it is running, a measure of
   progress that increases as the run advances and never decreases, and the time it last
   advanced. *(Also satisfies the progress half of SPEC-0005 #14.)*
8. **(R6)** An administrator watching a run sees its progress advance and sees it reach its
   terminal state without manually refreshing.
9. **(R7)** After an initial import is interrupted and resumed, the record shows how much was
   already stored and the point reached, and an administrator can relate the resumption to the
   earlier attempt for the same Órgano rather than seeing two unrelated runs.
10. **(R8)** A run whose process terminates without recording an end is not still reported as
    in progress indefinitely; it is distinguishable from a run that is genuinely executing.
11. **(R9)** When the source is unreachable, the failed run's record identifies the stage that
    failed and states that the source was unreachable; when the source returns an unusable
    response, the record states the status or the respect in which it was unusable — in both
    cases sufficiently that an administrator diagnoses the failure without consulting server
    logs.
12. **(R10)** For a run covering several Órganos where one fails, the record states which
    Órganos succeeded and which failed, with a reason per failure, in addition to the run's
    aggregate state.
13. **(R11)** No credential, token, key or connection password appears anywhere in any run
    record, including the diagnostics of a failed run and any recorded underlying error.
14. **(R12)** An administrator opening the page sees recorded runs most recent first with each
    run's target, mode, trigger, timing, duration, terminal state and counts; a run currently
    in progress is presented distinctly from finished runs.
15. **(R13)** The run list can be narrowed by target, by Órgano, by mode, by terminal state and
    by date, and each narrowing changes the reported count accordingly.
16. **(R13)** The run list is paginated: it states how many runs the current selection contains
    and how many pages it spans, an administrator can move between pages, and paging through
    the selection yields exactly that many runs with none repeated and none skipped.
17. **(R14)** Opening a single run shows everything recorded about it — including progress if
    it is running, resumption detail if it is resumable, and diagnostics if it did not fully
    succeed.
18. **(R15)** From a run of a per-Órgano import an administrator reaches that Órgano; from an
    Órgano they see its run history, when it was last successfully imported, and whether its
    initial import has completed.
19. **(R16)** An Órgano whose run records have been pruned is presented differently from an
    Órgano that has never been imported.
20. **(R17)** Run records older than the configured bound are no longer retained, while the
    most recent successful run for each Órgano and each importer is still retained however old
    it is — so "last imported successfully" remains answerable after pruning.
21. **(R18)** After run history is pruned, every imported contract and Órgano, every taxonomy
    placement, every import mark, and every record of a completed initial import is unchanged.
22. **(R19)** No function edits or individually deletes a run record.
23. **(R20)** When recording a run's progress or its outcome fails, the import itself still
    completes and its imported records are complete, unduplicated and consistent.
24. **(R21)** An import observed under this spec takes no materially longer than the same
    import unobserved, and progress is not recorded per contract.
25. **(R22)** With at least 100 000 retained run records on the target environment, the run
    list returns its first page and its count within 1 s at the 95th percentile, and a page
    deep into that history meets the same budget as the first page.
