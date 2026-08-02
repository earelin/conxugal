---
status: accepted
date: 2026-07-31
spec: SPEC-0005
supersedes: null
superseded_by: null
---

# 0017. Resumable import runs hold their state in PostgreSQL, in the record that reports them

## Status
Accepted

## Context
An Órgano's initial import is not a request; it is a job measured in days.
[SPEC-0005](../specs/SPEC-0005-import-browse-contratos-menores.md) R8 requires every
publication back to an Órgano's earliest, and the largest single Órgano — SERGAS — has
published on the order of 1.4 million contratos menores.
[ADR-0014](0014-resilient-throttled-outbound-http-client.md) caps us at one in-flight request
per source and no faster than one per second, deliberately. Those two facts multiply into a run
that outlives any deployment, restart or crash that happens to land on it.

So SPEC-0005 R9 requires an interrupted initial import to retain what it stored and to be
**resumed to completion without an administrator intervening**, adding no duplicates. That
obliges the run to remember, across a process boundary, how far it got.

[SPEC-0007](../specs/SPEC-0007-monitor-import-runs.md) then requires that the same facts be
**seen**: a measure of progress that advances and when it last advanced (R5), how much was
already stored and the point reached on a resumption, related to the earlier attempt for the
same Órgano (R7), and a run whose process died ceasing to be reported as in progress within a
bounded period (R8). Both specs list this as an open, ADR-grade decision, and SPEC-0007's says
explicitly that its records must not be read as binding that state to itself **nor as requiring
two separate stores**. Whichever record answers "where has this job got to?" for the resumer
also answers it for the administrator; they are the same question asked by two readers.

Three properties of what already exists constrain the answer. PostgreSQL is the datastore
([ADR-0001](0001-backend-stack.md)) and holds the imported contracts themselves. Ingestion runs
**in the same JVM as the API** (ADR-0014), so the process that writes progress and the process
that serves the monitoring page are one — but that is a fact about today's topology, not a
guarantee, and in-memory state would be betting on it. And SPEC-0005 R11 and R12 make import
**idempotent**: re-reading a publication already stored updates it in place and creates no
duplicate.

The alternative homes fail on the first requirement rather than on cost. In-memory job state
does not survive the restart that R9 exists to recover from. A dedicated scheduler or queue
(Quartz, a broker) would hold execution state in a vocabulary of its own, leaving SPEC-0007 to
mirror it into a second store and keep the two agreeing — the outcome SPEC-0007 named and
declined — and adds a moving part for a job list currently numbering one per Órgano.

## Decision
A run's state lives in **PostgreSQL, in the same row that SPEC-0007 reports**. There is one
record per run, written when the run is triggered and completed in stages as it advances; the
resumption point, the progress measure and the time it last advanced are **columns on that
record**, not a parallel structure that the monitoring record copies. The resumer and the
monitoring page read the same row.

**Progress is written outside the transaction that writes contracts.** An import commits
imported contracts in batches; after a batch commits, the run record is advanced in its own
short transaction. Sharing one transaction would make progress invisible until the whole batch
committed, which fails SPEC-0007 R5's requirement that progress advance often enough that a
running import is never read as abandoned, and would make a recording failure roll back
imported data, which SPEC-0007 R20 forbids outright — there, the import wins and the record is
what is sacrificed. So a failed progress write is logged and abandoned; it never propagates into
the import.

**The resumption point is therefore a conservative hint, not a ledger.** A crash between a data
commit and its progress write leaves the record pointing slightly behind what is stored, and a
resumption re-reads that overlap. This is safe, and it is safe *because* SPEC-0005 R11 and R12
already require re-reading a stored publication to update it in place rather than duplicate it.
The alternative — making the two writes atomic — would buy exactness by reintroducing the
coupling the previous paragraph exists to break. We take the overlap.

**Liveness is derived from the last-advanced timestamp, not maintained by a sweeper.** A run
whose last advance is older than the configured bound reads as **abandoned** (SPEC-0007 R4);
nothing has to run on a schedule to make that true, and no background job can itself fail and
leave the page lying. Reads apply that rule in one place so no query can forget it.

**The single-import guard of SPEC-0005 R22 is a read of this same state**, and so is correct
across restarts: a trigger that finds any live run records a *refused* run (SPEC-0007 R4)
instead of starting. An in-memory lock would have forgotten, after a restart, about the run
still recorded as executing — and because R21's guard is system-wide, forgetting it would put a
second run against the source, which is the outcome that requirement exists to prevent.

**PostgreSQL is what serialises that guard, not application code.** Reading *is there a live
run?* and inserting the new one must be **one act**: two triggers — a mark, an administrator, a
scheduler — can both read *no live run* and both insert, and the guard is the only thing
standing between this system and a public source that
[ADR-0014](0014-resilient-throttled-outbound-http-client.md) says owes us nothing. So a claim
runs in a single transaction that first takes a **transaction-scoped advisory lock**
(`pg_advisory_xact_lock`) on one key shared by every importer, then applies the liveness rule
above, then inserts; the lock releases on commit. A second claimant blocks on the lock and,
when it proceeds, sees the first one's committed row.

A **partial unique index** admitting one live run row was considered and does not work here: an
index predicate cannot reference `now()`, so a run past the abandonment bound keeps satisfying
it, and inserting past such a row would mean **writing** it to an abandoned state — which is
exactly what the paragraph above declines to do, and what the corresponding consequence below
accepts we do not do. The lock buys the same serialisation without that write. What it costs is
that the guarantee lives in the claim path rather than in the schema: **a run row is inserted in
exactly one place**, and a second insertion path would silently bypass the guard.

Not decided here: the schema, how retention is enforced (SPEC-0007 R17), and how live progress
reaches the browser — [ADR-0009](0009-sse-admin-realtime-metrics.md) and SPEC-0007's own open
decision own that, and this record only guarantees there is a durable thing to read.

## Consequences

### Pros
- An interrupted initial import resumes on its own, which is the requirement that motivated the
  record; nothing is lost to a deployment landing mid-run.
- One row is the single answer to "how far has this got?", so the resumer and the administrator
  cannot disagree — the divergence SPEC-0007 anticipated is structurally unavailable rather than
  something two components must be kept in step about.
- Progress and imported data commit through the same datastore and the same transaction manager,
  so there is no second store to provision, back up, or reconcile after a crash.
- Liveness costs nothing to maintain: no scheduled reaper, and no window in which the reaper's
  own failure makes the monitoring page untrustworthy.
- The concurrency guard survives restarts for free, because it reads durable state rather than
  process memory.
- Serialising the claim needs no new infrastructure: the advisory lock is held by the database
  already storing the runs, so there is no lock service to run, and a claimant that dies mid-claim
  releases it when its transaction ends rather than leaving a lock nobody owns.

### Cons
- **Resumption re-reads an overlap**, paced at ADR-0014's one request per second. A crash late
  in a batch costs that batch again in wall-clock time, and the cost scales with batch size —
  which now trades throughput against re-read cost rather than being a free tuning knob.
- **Progress writes add write load to the same database the import is filling**, on the busiest
  path in the system. SPEC-0007 R21 budgets 5 % of the import's own processing time, excluding
  time spent waiting on the source, for observation; batching the progress writes is what keeps
  them inside it, and that is now a constraint on the implementation rather than an
  optimisation. Batching is bounded from the other side by R5: batches coarser than R8's
  abandonment bound would make a healthy run read as dead, so the two are chosen together.
- **A derived abandoned state is not stored.** A run's row can say "in progress" indefinitely,
  and only the read applies the bound — so any reader bypassing that one place (an ad-hoc query,
  a future report, a migration) sees a stale answer. Writing the state would have made the row
  self-describing.
- **The guard is enforced by a lock, not by a constraint.** The database serialises the claim,
  but nothing in the schema forbids a second live run row — so a future code path that inserts
  one without taking the advisory lock defeats R22 silently, and no `INSERT` will fail to warn
  it. This is the price of not writing the abandoned state, and it makes "runs are claimed in
  one place" a property to test rather than one the schema guarantees.
- **"When it reached abandoned" is computed, not observed** — last advance plus the bound — so
  it is an inference where every other terminal timestamp in SPEC-0007 R3 is a fact.
- **Recording is best-effort by construction.** Because a progress write may fail without
  failing the import, a run can complete correctly while its record understates it; that is
  SPEC-0007 R20's explicit ordering, but it means the record is evidence about the import rather
  than part of it.
- Long-running rows are updated repeatedly over days, which is churn PostgreSQL handles through
  autovacuum but which is worth knowing about before the table is designed.
