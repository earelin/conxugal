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
else. A scheduled run has no requester: SPEC-0004 and SPEC-0005 both promise automatic
imports, and neither leaves anything an administrator can look at afterwards. An initial
import of a large Órgano runs for hours and may be interrupted and resumed several times
before it completes, so "did it work?" is not even a question with a single answer. And both
specs say a failure is *"reported to the administrator (for a manual run) **or otherwise
recorded**"* — a clause with no requirement behind it and no acceptance criterion to prove.
This spec is what "otherwise recorded" means, and it is what discharges SPEC-0005's promise
that while an initial import is in progress an administrator can see that it is running and
how far it has got.

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
metrics are transient by requirement (SPEC-0003 forbids the backend storing them), while
import history is durable by definition. What the two share is an expectation of liveness,
and there is prior art for it in
[ADR-0009](../architecture/0009-sse-admin-realtime-metrics.md).

## Scope

Deliberately **out of scope**:

- **Triggering, scheduling and performing imports.** Owned by SPEC-0004 and SPEC-0005,
  including resuming an interrupted initial import and requesting a historical re-read. This
  spec **observes**; it never starts, stops or re-runs anything. A run's existence, its mode
  and its lifecycle are facts it records, not decisions it makes.
- **What an import does to the data.** Reconciliation, identity, idempotency and retention of
  imported records belong to the importing specs.
- **Transient runtime metrics.** SPEC-0003 owns the live JVM/datastore metrics view, which the
  backend must not persist. Import history is the opposite kind of data and does not belong
  there.
- **User-facing import status.** SPEC-0005 deliberately makes several empty-list cases
  distinguishable *to a `USER`* — not imported, initial import unfinished, imported and empty,
  filtered to nothing. That surface stays SPEC-0005's. This spec adds no `USER` surface at all
  (R1).
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
   already chose Server-Sent Events for admin live data and would be the natural precedent.
   Whether this capability reuses that stream or introduces its own is a decision worth
   recording either way: ADR-0009's reasoning is written around transient metrics
   specifically, so carrying a second, differently-shaped payload over it is a change to that
   decision's context, not merely an application of it.
2. **Where a run's progress, its resumption point, and its liveness are held.** SPEC-0005
   already lists "how a long-running, resumable import job holds its state" as an open,
   ADR-grade decision. R5, R7 and R8 here describe what an administrator must be able to
   *see* — including that an abandoned run stops being reported as running; they must not be
   read as binding that state to this spec's records, nor as requiring two separate stores.
   Whichever ADR settles the job state should settle all three with it.
3. **How retention is enforced.** R17 fixes the bound and the exception to it, not the
   mechanism or the cadence.
4. **How the run list is paged.** R13 requires jumping to a chosen page and an exact count,
   and R22 requires a deep page to cost what the first does. At this spec's volume those hold
   together comfortably; they are stated here so the paging ADR that SPEC-0005 and SPEC-0006
   trigger covers this surface too rather than leaving it to diverge.

## Requirements

### Access

- **R1** — Every function in this spec — the run list, its filters, a run's detail and its
  diagnostics (R12–R14), the Órgano-side history route (R15), and live progress (R6) — is
  reachable only by users with the `ADMIN` role. A `USER` or an unauthenticated visitor who requests any of them is denied (consistent
  with SPEC-0003's administration-area access rule). There is **no `USER`-facing surface**:
  import history is operational data about the system, not information about contracts.

### Recording every run

- **R2** — **Every import run is recorded**, whichever importer performs it, whatever mode it
  runs in, and whatever triggers it. That covers every importer the system has or gains — the
  Órgano catalogue import and each contratos menores mode today, any later contract family
  without amending this requirement. Recording is not conditional on a human being present to
  receive the outcome: the automatic runs the importing specs promise are precisely the ones
  with no other witness. This requirement is what SPEC-0004 and SPEC-0005 mean by *"or
  otherwise recorded"* when they describe a failure.
- **R3** — A run's record comes into existence **when the run is triggered** — by an
  administrator, by the scheduler, or by an automatic resumption — and is completed in stages
  as the run progresses. A record is **complete for its stage**, never incomplete.

  **From the moment it is triggered**, the record states **when it was triggered**; **what is
  being imported** — which importer, and for a per-Órgano import which Órgano; the **mode** it
  runs in; and **what triggered it**, distinguishing an administrator from the scheduler from
  an automatic resumption, and naming the administrator for a manual trigger. Every record has
  a trigger time, including one that never runs, so every record can be ordered and
  date-filtered (R12, R13).

  **If the run starts**, the record states **when it started**. A run refused under R4 never
  starts and so has no start time.

  **When the run reaches a terminal state**, the record states that state (R4), **when it
  reached it**, and the **counts the importer reports** — for contratos menores, contracts
  added and refreshed; for the catalogue, Órganos added, refreshed and marked inactive. A
  **refused** run is terminal at the moment it is refused, having neither started nor run, and
  so carries no start, no duration and no counts. A run still executing has no terminal state,
  no end and no counts either — what it has instead is progress (R5), which is not a count of
  what the importer reported.
- **R4** — An outcome distinguishes, at minimum:
  - **succeeded** — it completed and did what it set out to do;
  - **failed** — it could not;
  - **partially succeeded** — some of what it covered succeeded and some did not. This is not
    an edge case: SPEC-0005 *requires* a run to carry on past a failing Órgano, so a
    multi-Órgano run is more likely to end here than in either of the above;
  - **stopped** — ended deliberately rather than by failure, as when unmarking an Órgano halts
    an import in progress;
  - **refused** — not started because an import of that same target was already in progress. A
    refused trigger is recorded rather than silently dropped, because a trigger that did
    nothing must not be indistinguishable from one that ran;
  - **abandoned** — it stopped reporting and never reached any of the above, which is the state
    R8 gives a run whose process died. It is deliberately not *failed*: nothing observed a
    failure, and recording one would assert more than is known.

  These states describe **both a run as a whole and each Órgano within it**. A run covering
  many Órganos can succeed for most, fail for one, be refused for another that was already
  importing, and be stopped for a third that was unmarked while the run was under way — so the
  same vocabulary has to work at both levels, and *partially succeeded* is how a run reports
  that its Órganos did not all end the same way. A run or an Órgano currently executing is
  **in progress**, which is not a terminal state.
- **R5** — While a run is in progress its record reports **progress**: that it is running, a
  measure of how far it has got that only ever advances, and **when it last advanced**. For an
  initial import of over a million contracts, "running" is not progress — an administrator's
  actual question is whether it is advancing or stalled, and only the last two of those three
  answer it.
- **R6** — Progress and a run's transition to a terminal state reach a watching administrator
  **live**, without them manually refreshing — the same expectation SPEC-0003 sets for its
  detailed-metrics view. The transport is decided outside this spec.
- **R7** — For a run that can be resumed, the record states **how much was already stored** and
  **the point reached**, and successive attempts at one Órgano's initial import are relatable
  to one another, so an administrator can see that a resumption continued the load rather than
  restarted it, and can see an Órgano converging over several attempts instead of a sequence
  of unrelated failures.
- **R8** — A run whose process dies without ever recording an end is **not left reported as in
  progress**. Within a bounded, configurable period it takes the **abandoned** state of R4,
  becoming distinguishable from one genuinely still executing — otherwise the single most
  important thing the page shows, what is running now, is the thing it is least able to be
  trusted about. Naming the state matters as much as bounding the period: without a name, two
  implementations could record an abandoned run as *failed* and as *stopped* and both be
  defensible. The period is not fixed here; that it is bounded is.

### Diagnosing failures

- **R9** — A record that **failed or partially succeeded**, at the level of the run or of any
  Órgano within it, carries enough detail to diagnose the failure **without reading server
  logs**: which stage of the run failed, what the source did — unreachable, refused the
  request, or returned a response the system could not use, together with the status or the
  respect in which it was unusable — the underlying error, and **which source publication the
  failure concerned**, identified by the publication identifier its family stores. That last
  is what keeps R11 survivable: barred from reproducing awardee data, a diagnostic still has to
  say *which* of a million publications broke, and the publication identifier is not personal
  data. This is the requirement the capability exists for; a record that says only "failed"
  moves the debugging problem rather than solving it.

  A **refused**, **stopped** or **abandoned** outcome carries a **reason** rather than
  diagnostics: nothing failed, so there is no failing stage and no source interaction to
  describe.
- **R10** — For a run covering several Órganos, the record states the **outcome per Órgano**
  under R4's vocabulary — which succeeded, which failed and why, which were refused because
  that Órgano was already importing, and which were stopped — not only the run's aggregate
  state. SPEC-0005 guarantees the run continues past a failing Órgano, so an aggregate verdict
  alone hides the only thing an administrator needs: *which* Órgano needs attention, and
  whether it was even attempted.
- **R11** — **No secret and no personal data ever appears in a record.** No credential, token,
  key or connection password, whatever the underlying failure was (the same rule SPEC-0003
  applies to its status and metrics views). Nor any awardee name or fiscal identifier: a
  diagnostic exists to identify a technical failure, and reproducing an awardee's personal
  data into a record retained under R17 serves no debugging purpose. Together these are what
  make R9's technical detail safe to retain and to display.

  This bars **reproducing** personal data, not **pointing at** the record that carried it: the
  publication identifier R9 requires is assigned by the source and identifies a publication,
  not a person, so a diagnostic can always say which publication broke without saying who it
  named.

### Reviewing runs

- **R12** — An administrator can open a page listing recorded runs, **most recent first** by
  trigger time, showing for each what R3 records **for that run's stage** — what was imported,
  its mode, what triggered it and when, and where the stage supplies them, its duration, its
  outcome and its counts. A refused run showing no duration and no counts is displaying a
  complete record, not a broken row. Runs **in progress** are presented distinctly from
  finished ones: they are the operationally urgent rows, and sorting alone does not make them
  stand out.
- **R13** — The list can be **narrowed** — by what was imported, by Órgano, by mode, by what
  triggered it, by **state including *in progress***, and by when the run happened — and is
  **paginated**: an
  administrator sees one page at a time, is told how many runs the current selection contains
  and how many pages it spans, and can move to the next or previous page or jump to a chosen
  one. Narrowing by trigger is what answers *"what has the scheduler been doing?"*, which is
  otherwise unaskable. History accumulates at the scheduler's cadence multiplied by the number
  of marked Órganos, so an unfiltered, unpaginated list stops being usable long before it
  stops being correct.
- **R14** — An administrator can open a **single run** and see everything R3 records about it,
  together with R5's progress if it is still running, R7's resumption detail if it is
  resumable, and R9's and R10's diagnostics if it did not fully succeed.
- **R15** — History is navigable **from the Órgano as well as from the run**: from a run of a
  per-Órgano import an administrator reaches the Órgano it covered, and from an Órgano they
  reach that Órgano's own run history — including **when it was last imported successfully**,
  and whether its initial import has completed (a fact SPEC-0005 already requires the system
  to hold, and which no administrator-facing surface shows). *Is this Órgano actually up to
  date?* is the question the capability is most often opened to answer, and it is asked about
  an Órgano, not about a run. This Órgano-side route is an **`ADMIN`-only affordance** and
  leaves the `USER`-readable catalogue and taxonomy views of SPEC-0004 unchanged: R1 admits no
  `USER` surface, so this history is never reached by enriching a shared view. It is hosted on
  the `ADMIN`-only surface where an administrator already chooses which Órganos are imported
  ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R4), which is the one place an
  administrator already looks at Órganos one at a time — naming it here so no feature has to
  decide the question, and so this spec adds no third Órgano screen of its own.
- **R16** — An Órgano with **no retained runs** is not presented as one that was **never
  imported**. Retention is bounded (R17), so those two states converge over time unless they
  are kept apart deliberately.

### Retention and integrity

- **R17** — Run history is retained for a **bounded** period or count rather than
  indefinitely, because its volume grows without limit with the scheduler's cadence. The bound
  has one exception: for each Órgano and each importer, the most recent run **in which that
  Órgano itself succeeded** is always retained regardless of age. The exception is keyed on
  the Órgano's own outcome (R10), not on the run's aggregate state, because R4 makes
  *partially succeeded* the likely verdict on a multi-Órgano run — so keying it on a
  successful *run* would discard exactly the last-success fact R15 promises stays answerable.
  For an importer whose runs are **not** per-Órgano — the catalogue import, which reports no
  per-Órgano outcome under R10 — the exception keys on the importer alone: its most recent
  successful run is always retained. Pruning is automatic; the bound is configurable and is not
  fixed here.
- **R18** — Pruning run history **never touches imported data**: no contract, Órgano, taxonomy
  placement, import mark, or record of a completed initial import is altered or lost when a
  run record is discarded. Monitoring data is derived from the act of importing and is
  disposable; what was imported is not — and the importing specs never delete it.
- **R19** — Run records are **not editable by anyone**. No administrative function amends or
  deletes an individual run — pruning under R17 is automatic and wholesale, never selective.
  This constrains what the administration area offers, not what the system writes: completing
  a record as its run advances through R3's stages, and advancing R5's progress, are how a
  record is built, not amendments to it. A debugging trail that can be selectively rewritten is
  not a trail, and an operational record that can be tidied cannot be used to establish what
  happened.

### Non-functional expectations

- **R20** — **Recording never breaks the import.** A failure to record a run's start, its
  progress or its outcome does not fail, abort or alter an import that would otherwise have
  succeeded, and never causes an imported record to be lost, duplicated or left inconsistent.
  Where this collides with R2 — a run that happened but could not be recorded — **this
  requirement wins**: the import proceeds and the record is what is sacrificed. Observability
  is subordinate to the thing it observes; the failure mode where monitoring takes down the
  import it was added to protect is the one to design out.
- **R21** — **Recording is cheap.** Observing an import does not slow it by more than **5 %**,
  measured on the **reference environment** SPEC-0005 R23 defines and shares across all three
  specs, over an initial import of at least 100 000 contracts. The comparison is against the
  same import with recording disabled — a **measurement configuration**, not a state the
  running system admits, since R2 makes recording unconditional. R5 requires progress that
  advances and is timely, which this budget leaves ample room for.
- **R22** — The monitoring page stays responsive at the volume history reaches. Measured on
  the same **reference environment**, under at least **10 concurrent readers** as SPEC-0005 R23
  stipulates, holding whichever is **greater** of the maximum volume R17's configured bound
  permits and **100 000** retained run records: the run list returns its first page and its
  count within **1 second at the 95th percentile**, and a page deep into that history meets the
  same budget as the first. Unlike the contract and operador lists, this volume is small enough
  that paging need not degrade with depth at all, so the budget here is uniform rather than
  tiered.

## Acceptance criteria

1. **(R1)** A `USER` or an unauthenticated visitor that requests the run list, a run's detail,
   or live progress is denied; an authenticated `ADMIN` is allowed. No `USER`-facing screen
   exposes import run history, and the Órgano-side route of R15 is absent for a `USER`.
2. **(R2)** After a **scheduled** import runs with no human trigger, a record of it exists and
   is visible to an administrator afterwards. *(This is the case that has no other witness, and
   what SPEC-0004 and SPEC-0005 mean by "otherwise recorded".)*
3. **(R2)** A record exists for each of: the catalogue import, an Órgano's initial import, an
   incremental import, a resumption of an incomplete initial import, and a historical re-read.
4. **(R3)** From the moment a run is triggered, and before it ends, its record states its
   trigger time, what is being imported, its mode and its trigger — naming the administrator
   for a manual trigger and identifying the scheduler for a scheduled one — and, once the run
   starts, its start time.
5. **(R3)** Once a run reaches a terminal state, its record additionally states that state,
   when it reached it, and its counts.
6. **(R3)** A **refused** run carries a trigger time but no start, no duration and no counts,
   and is terminal from the moment it is refused; it is orderable and date-filterable in the
   run list on its trigger time like any other record, and is not treated as malformed.
7. **(R4)** A run that completed, one that failed outright, one that covered several Órganos of
   which some failed, one halted by unmarking its Órgano, and one refused because an import of
   that target was already running are each recorded in a **distinguishable** outcome; a run
   still executing is recorded as in progress and not as any terminal state.
8. **(R4)** Triggering an import of an Órgano already being imported produces a record of a
   **refused** run, rather than no record at all.
9. **(R4, R10)** In a single run covering several Órganos where one succeeds, one fails, one is
   already importing and one is unmarked mid-run, the record shows those four Órganos in four
   distinguishable outcomes, and the run as a whole reports **partially succeeded**.
10. **(R5)** While an initial import runs, its record reports that it is running, a measure of
   progress that increases as the run advances and never decreases, and the time it last
   advanced. *(Also satisfies the progress half of SPEC-0005's initial-import criterion.)*
11. **(R6)** An administrator watching a run sees its progress advance and sees it reach its
    terminal state without manually refreshing.
12. **(R7)** After an initial import is interrupted and resumed, the record shows how much was
    already stored and the point reached, and an administrator can relate the resumption to the
    earlier attempt for the same Órgano rather than seeing two unrelated runs.
13. **(R4, R8)** A run whose process terminates without recording an end stops being reported as
    in progress within the configured period and is recorded as **abandoned** — not as failed
    and not as stopped — so two implementations cannot label the same event differently and
    both pass.
14. **(R9)** When the source is unreachable, the failed run's record identifies the stage that
    failed and states that the source was unreachable; when the source returns an unusable
    response, the record states the status or the respect in which it was unusable — in both
    cases sufficiently that an administrator diagnoses the failure without consulting server
    logs.
15. **(R10)** For a run covering several Órganos where one fails, the record states which
    Órganos succeeded and which failed, with a reason per failure, in addition to the run's
    aggregate outcome.
16. **(R11)** When an import fails because the datastore or the source rejects the system's
    credentials, the recorded diagnostics identify the failure without reproducing the
    credential, token, key or password anywhere in the record.
17. **(R9, R11)** When an import fails on a source record the system cannot parse, the recorded
    diagnostics identify what was unusable about it **and name the publication identifier**, so
    an administrator can find that publication among millions — without reproducing any awardee
    name or fiscal identifier from it.
18. **(R12)** An administrator opening the page sees recorded runs most recent first by trigger
    time, each showing what R3 records for its stage — and a list containing a refused run and
    an in-progress run alongside finished ones renders all of them, the refused row showing no
    duration and no counts without being treated as malformed. A run currently in progress is
    presented distinctly from finished runs.
19. **(R13)** The run list can be narrowed by target, by Órgano, by mode, by trigger, by state
    and by date, and each narrowing changes the reported count accordingly; narrowing by
    trigger returns the scheduler's runs and no others, and narrowing by state to **in
    progress** returns exactly the runs currently executing.
20. **(R13)** The run list is paginated: it states how many runs the current selection contains
    and how many pages it spans, an administrator can move to the next and previous page and
    jump to a chosen page, and paging through the selection yields exactly that many runs with
    none repeated and none skipped.
21. **(R14)** Opening a single run shows everything recorded about it — including progress if
    it is running, resumption detail if it is resumable, and diagnostics if it did not fully
    succeed.
22. **(R15)** From a run of a per-Órgano import an administrator reaches that Órgano; from an
    Órgano they see its run history, when it was last successfully imported, and whether its
    initial import has completed.
23. **(R16)** An Órgano whose run records have been pruned is presented differently from an
    Órgano that has never been imported.
24. **(R17)** Run records beyond the configured bound are no longer retained, whether the bound
    is expressed as an age or as a count.
25. **(R17)** An Órgano that succeeded inside a run whose aggregate outcome was **partially
    succeeded** still has that run retained as its most recent success after pruning, and
    "last imported successfully" remains answerable for it.
26. **(R18)** After run history is pruned, every imported contract and Órgano, every taxonomy
    placement, every import mark, and every record of a completed initial import is unchanged.
27. **(R19)** The administration area exposes no operation that amends or deletes an individual
    run record.
28. **(R20)** When recording a run's progress or its outcome fails, the import itself still
    completes and its imported records are complete, unduplicated and consistent — the record
    is what is lost, not the import.
29. **(R21)** On the reference environment, an initial import of at least 100 000 contracts
    takes no more than 5 % longer with recording enabled than the same import measured with
    recording disabled — a measurement configuration, since the running system always records.
30. **(R22)** On the reference environment, under at least 10 concurrent readers, holding
    whichever is greater of the maximum volume R17's bound permits and 100 000 retained run
    records, the run list returns its first page and its count within 1 s at the 95th
    percentile, and a page deep into that history meets the same budget as the first page.
31. **(R17)** For the catalogue import, whose runs carry no per-Órgano outcome, the most recent
    successful run is retained after pruning, so its last-success fact stays answerable too.
