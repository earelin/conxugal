---
feat: FEAT-0009
domain: backend
adrs: [0002, 0008, 0017, 0019]
status: done
depends_on: [TASK-0003]
---

# Import run record, the abandoned rule, and the system-wide guard

The durable run record of
[ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md), the read rule that keeps
a dead run from wedging the system, and R22's single-import guard built on both. No importer
uses it yet: [TASK-0008](TASK-0008-adopt-the-guard-in-the-catalogue-import.md) adopts it in the
catalogue import and [TASK-0010](TASK-0010-multi-organo-orchestration.md) in this one, on
FEAT-0006's own build-then-adopt precedent.

**Only the columns this feature's own guard, resumer and R20 outcome need.** ADR-0017 decides
where the state lives and says explicitly that the schema is not decided there, so no column
here is justified by "SPEC-0007 will want it";
[SPEC-0007](../../specs/SPEC-0007-monitor-import-runs.md)'s features widen these same rows
rather than opening a second store.

**It depends on [TASK-0003](TASK-0003-contrato-menor-domain-model.md) for one reason only:**
that task proves Micronaut Data can return a `@GeneratedValue` key through an
`AttributeConverter`, which is the mechanism `ImportRunId` below also rests on
([ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md)). Nothing else here needs a
contrato menor.

## Scope
- **`ImportRunId`, a record wrapping a `UUID`**, beside the run aggregate, with its
  `AttributeConverter` — ADR-0019's pattern, for the identifier this feature threads through the
  most hands: a claim returns it, a trigger answers with it, the run read is keyed by it and the
  UI holds it. The column stays a plain `uuid`.
- A migration (next free `V` number) creating two tables.

  `import_run` — one row per run, written **when the run is triggered**:

  | Column | Why it is here |
  | --- | --- |
  | `id UUID PRIMARY KEY` | What a trigger returns and the run read is keyed by — an `ImportRunId` in Java |
  | `importer TEXT NOT NULL` | `ORGANOS` or `CONTRATOS_MENORES` — the guard is system-wide across both (R22) |
  | `state TEXT NOT NULL` | `IN_PROGRESS` / `SUCCEEDED` / `PARTIALLY_SUCCEEDED` / `FAILED` — R20's verdict set. **No `ABANDONED` value**: that state is derived on read, never stored (ADR-0017) |
  | `started_at TIMESTAMPTZ NOT NULL` | R20's outcome |
  | `finished_at TIMESTAMPTZ` | R20's outcome |
  | `last_advanced_at TIMESTAMPTZ NOT NULL` | The guard's liveness bound (ADR-0017) |
  | `added INT NOT NULL DEFAULT 0`, `refreshed INT NOT NULL DEFAULT 0` | R20's counts. Named importer-neutrally because the catalogue import writes Órgano counts into the same columns |

  **Two columns the feature's design table names are deliberately absent.** That table assigns
  *"run identity, trigger, scope, times, state, counts"* to the run record; `trigger` and
  `scope` are not columns here, because nothing this feature meets reads them — the run read
  reports the covered Órganos, which *is* the scope, and no requirement here distinguishes a
  mark-triggered run from an administrator-triggered one. SPEC-0007 R2 and R4 are what will
  want them, and this feature's own rule is that no column is justified by that. The feature
  README's table should be read as the fact set, not the column set.

  `import_run_organo` — the covered Órganos, **enumerated when the run is triggered**, not
  discovered as the run reaches them, keyed `PRIMARY KEY (run_id, organo_id)` so one run cannot
  cover an Órgano twice: `run_id`, `organo_id`, `state`
  (`PENDING` / `IN_PROGRESS` / `SUCCEEDED` / `FAILED` / `STOPPED` / `SKIPPED`), `added`,
  `refreshed`, and a nullable failure reason. R20 requires the outcome to name *which Órganos
  were covered and which of them failed*, and a run that dies at the fortieth of four hundred
  cannot reconstruct the list it was going to cover.
  - `STOPPED` is the Órgano unmarked mid-run (R5, #8) — deliberate, not a failure. `SKIPPED` is
    the `COMPLETE` Órgano whose incremental mode this feature does not implement. Both exist
    because a run that reported them as failures would report the normal case as a lie.
- **No `REFUSED` run state and no refused rows** — a knowing divergence from ADR-0017's
  Decision, which says a trigger finding a live run *"records a refused run (SPEC-0007 R4)"*.
  SPEC-0005 #32's own note settles it for this feature: *a task claiming this criterion owes
  the guard, not the record*, and the record it names is SPEC-0007 R4's. So the refusal is
  reported to whoever triggered ([TASK-0011](TASK-0011-triggers-and-run-read.md)) and stored
  nowhere; SPEC-0007's feature adds the row, in this same table, without changing anything
  built here.
- `ImportRunRepository` port and its JDBC implementation: claim, advance, complete, and read
  one run with its per-Órgano rows.
- **The derived-abandoned read, applied in one place.** A run whose `last_advanced_at` is older
  than the configured bound reads as abandoned; **nothing sweeps and nothing writes it** — the
  row still says `IN_PROGRESS` on disk, exactly as ADR-0017 accepts. Without the rule one crash
  mid-run blocks every import in the system forever, since SPEC-0007 R19 leaves no
  administrative function to clear a row.
- **The guard is one act, serialised by a PostgreSQL transaction-scoped advisory lock**
  (`pg_advisory_xact_lock` on a fixed key shared by both importers) — the mechanism
  [ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md) decides; this task
  builds it. A claim is a single
  transaction: take the lock, look for a run that is `IN_PROGRESS` **and advanced within the
  bound**, refuse if there is one, otherwise insert the new run row. The lock releases on
  commit. Two triggers — the mark control and an admin trigger today, the scheduler once the
  incremental feature lands — can both read *no live run*, which is why the check and the write
  have to be inside one lock rather than merely inside one method.

  ```mermaid
  sequenceDiagram
      participant T as trigger
      participant DB as PostgreSQL
      T->>DB: BEGIN, then pg_advisory_xact_lock(import-guard)
      Note over T,DB: a concurrent claim waits here
      T->>DB: SELECT the run that is IN_PROGRESS<br/>and advanced within the bound
      alt a live run answers
          T->>DB: ROLLBACK
          Note over T: refused — the guard is held
      else none, or only stale rows
          T->>DB: INSERT the run and its covered Órganos, then COMMIT
          Note over T: claimed — the lock releases on commit
      end
  ```

- **No partial unique index admitting one `IN_PROGRESS` row.** ADR-0017 records why — an index
  predicate cannot reference `now()`, so a stale row keeps satisfying it and inserting past one
  would mean *writing* the abandoned state that same record declines to store. What the lock
  costs is that the guarantee lives in the claim rather than in the schema, so the claim must be
  the **single** place a run row is inserted; the test below is what pins that.
- **Batch size and the abandonment bound are one decision, taken here**, because batches
  coarser than the bound make a healthy run read as dead and batches finer than necessary spend
  write load on the busiest path in the system (ADR-0017):
  - **A batch is one source page — 100 rows**, so progress advances roughly once per outbound
    request. At ADR-0014's one-request-per-second ceiling that is one short transaction per
    request-second, which is small beside the batch upsert it follows. The 100 is **not a
    knob**: it is the source's measured page cap
    ([`design/source-contract.md`](design/source-contract.md)), enforced by
    [TASK-0005](TASK-0005-source-port-and-adapter.md)'s adapter and paged by
    [TASK-0009](TASK-0009-single-organo-initial-import.md). Tying the batch to the page is what
    makes the batch size a consequence of the source rather than a second thing to tune.
  - **The bound defaults to 15 minutes.** The longest legitimate gap between advances is one
    slice's worth of ADR-0014's shipped settings — up to 10 s waiting for a permit, 3 attempts
    at a 10 s read timeout with backoff — roughly a minute at worst. Fifteen gives an order of
    magnitude of headroom while keeping a crashed run from holding the system for longer than
    an administrator will wait.
  - The **bound** is configuration (`@ConfigurationProperties`, per the project's binding
    convention); the batch is not, for the reason above. The reasoning is what the task
    records.

## Acceptance criteria
- While a run is `IN_PROGRESS` and advancing, a second claim — of either importer — is refused
  and inserts nothing.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #32)
- Two claims issued concurrently yield exactly one run: the loser waits on the lock and then
  finds the winner's row, rather than both passing a check and both inserting. (SPEC-0005 #32)
- A run whose `last_advanced_at` is older than the bound no longer holds the guard: the next
  claim succeeds, and the stale run's row is **untouched** — still `IN_PROGRESS` on disk, with
  its counts and timestamps as they stood — while every read of it reports abandoned. A run
  advanced within the bound still holds the guard. (SPEC-0005 #32)
- The covered Órganos are readable from the run record immediately after the claim, before any
  of them has been touched. (SPEC-0005 #29)
- Completing a run records its verdict, its finish time and its counts; the per-Órgano rows
  carry their own state and counts. (SPEC-0005 #29, #30)
- The abandoned rule exists in exactly one place — asserted architecturally or by there being a
  single query that applies it — so no reader can bypass it, and no code path stores it.
- A run row is inserted in exactly one place, the claim, so the advisory lock cannot be
  side-stepped by a second insertion path. This is the guarantee the abandoned partial-unique
  index would have given for free; here it is asserted rather than enforced.
- Integration-tested against PostgreSQL (Testcontainers), including the concurrent-claim race
  and the stale-run release.
