---
feat: FEAT-0014
domain: backend
adrs: [0001]
status: todo
depends_on: [TASK-0004]
---

# The scheduler

`ContratosMenoresImportScheduler`: the recurring trigger that makes every import happen **without an
administrator asking for it**. One class and a cron, following `ImportOrganosScheduler`'s shape —
and **departing from it on the executor**, for the reason below.

**It depends on [TASK-0004](TASK-0004-incremental-branch-in-the-mode-rule.md), not on
[TASK-0005](TASK-0005-the-sweeps-order.md).** Without the incremental branch a nightly sweep would
skip every loaded Órgano and #31 would be unreachable, so task 4 is a hard prerequisite. The
ordering is a freshness optimisation this scheduler works without — it makes a mistimed mark cost
hours instead of days — so the two can land in either order, or in parallel.

**Everything it needs already exists**, which is why it is one class and a cron. It calls
`StartContratosMenoresImport.startAll()` and nothing else: which Órganos are eligible is
`ClaimContratosMenoresImport`'s, which mode each takes is the per-Órgano state's, the order they are
taken in is [TASK-0005](TASK-0005-the-sweeps-order.md)'s once that lands, and whether an import may
start at all is the guard's. A scheduler that filtered, ordered or chose modes would be a fourth trigger able to
disagree with the other three — the defect FEAT-0009 centralised the mode rule to prevent.

## Scope

- `@Scheduled(cron = "${conxugal.contratos-menores.import.schedule}", zoneId = "Europe/Madrid")`,
  with `conxugal.contratos-menores.import.schedule` shipped in `application.yml` as
  **`0 0 5 * * *`** and overridable by configuration. The property carries no `${…:default}`
  fallback, following its `conxugal.organos.import.schedule` sibling: the yaml value *is* the value,
  and an absent key fails startup rather than silently scheduling nothing. Daily, and **after**
  FEAT-0006's 03:00 catalogue import rather than beside it.
  Both would be safe under the guard, but one of two triggers firing at the same instant always
  loses, and losing nightly is not a schedule. Running after the catalogue means the day's
  deactivations are already applied, so an Órgano that stopped being published is not imported one
  last time.
- **A refusal is logged, not thrown**, and there is exactly **one** refusal to catch:
  `ImportAlreadyRunningException`. Were it to reach Micronaut's scheduled-task handler it would be
  an error report for the system working exactly as R22 says it must.
  `OrganoNotEligibleForImportException` is **not** a second one and must not be caught here:
  `claimAll()` claims over `findAllByActiveTrueAndImportableTrue()` and never throws it — a
  catalogue with nothing marked is *"an ordinary answer, not a refusal, and it settles as a success
  that imported nothing"*, in `ClaimContratosMenoresImport`'s own words. Only `claimOrgano` can
  raise it, and this scheduler never calls it.
- **Micronaut's default `scheduled` executor, and no executor of its own** — a deliberate departure
  from the FEAT-0006 precedent rather than a copy of it. `ImportOrganosScheduler` declares
  `organos-import` because `run()` calls the catalogue import **synchronously** and holds its thread
  for the whole run; that reason does not carry here, because `startAll()` claims and hands the walk
  to the `contratos-menores-import` executor FEAT-0009 already built for it, so this thread is
  occupied for milliseconds. It must not reuse `contratos-menores-import` — that is a `fixed` pool
  and `@Scheduled` needs a `TaskScheduler` — but *not that one* is not an argument for *a new one*,
  and `CLAUDE.md` says to prefer the framework's default over custom wiring.
  - **The consequence to state, because the precedent's tests assert the opposite:** the default
    `scheduled` pool is platform-threaded, so this scheduler's own thread is not virtual. Nothing
    here decides a threading model —
    [ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md) governs request handling, and
    the blocking work still runs on the virtual-threaded `contratos-menores-import` executor. So
    `ImportOrganosSchedulerFiringIntegrationTest`'s `assertThat(RUN_THREAD.get().isVirtual())` is
    **not** copied into this task's tests; asserting it would fail, and asserting it would be
    testing a decision this task deliberately does not take.
- **Two javadocs stop calling this a *future* scheduler**: `ContratosMenoresImportMode` (*"the
  manual trigger, the mark trigger and the future scheduler"*) and `ClaimContratosMenoresImport`
  (*"a mark, an administrator's button, a future scheduler"*). Both are true until this task and
  false after it.
- **The integration tests that only exist once something fires**, in
  `application/src/integrationTest/.../scheduling/contratosmenores/` and in the precedent's shape —
  `@MicronautTest` with `@MockBean`s and **no database**, as
  `ImportOrganosSchedulerGuardIntegrationTest` puts it, *"to keep this test off a database it has no
  question for"*:
  - the scheduler **fires on its configured cron** and calls `startAll()`, once per tick;
  - a tick **refused** while an import holds the guard logs and completes normally — nothing
    propagates to Micronaut's handler — and the **next tick claims**. The long import is simulated
    by a held guard rather than waited out, which is what SPEC-0005 #35 allows;
  - the scheduler passes straight through: it calls `startAll()` and never `startOrgano`, filters no
    Órgano and chooses no mode.
- **What these tests deliberately do not prove**, because a durable-state proof belongs with the
  durable state and this task adds none:
  - *the whole gap covered once the guard frees* is the **floor's** property and is proven against
    PostgreSQL by [TASK-0003](TASK-0003-the-incremental-walk.md), beside the existing
    `OrganoContratosMenoresImportIntegrationTest`;
  - *a half-loaded Órgano resumed with no administrator involved* is the **mode rule's**, shipped by
    FEAT-0009 and unchanged here. What this task adds to it is only the tick that arrives without an
    administrator, which the first test above covers.

**What a nightly sweep costs the source**, recorded here because it is what makes daily the right
interval: a loaded Órgano's refresh is one 89-day window covering a ~31-day period, and the page
loop stops as soon as the source returns a short page — so a quiet Órgano costs a **single**
request. Four hundred marked Órganos are a few hundred requests, on the order of **ten minutes** of
traffic once a day at ADR-0014's one-per-second ceiling, plus whatever the handful of large
publishers add. The pace is the client's; this task adds no second one, which is why SPEC-0005 R25
needs nothing new here.

**Out of scope:** recording that a run was triggered by the scheduler — SPEC-0005 asks nothing of
the sort, [SPEC-0007](../../specs/SPEC-0007-monitor-import-runs.md) R2 does, and the column lands
with the requirement that needs it. Likewise recording a refusal as a refused run, which stays
SPEC-0007 R4's; the scheduler's refusals are logged exactly as an administrator's are today.

## Acceptance criteria

- With **no human trigger**, the scheduler runs and contracts published since the previous run
  become browsable for every marked, active, initially-imported Órgano. Proven by composition: the
  firing test here, over the refresh [TASK-0003](TASK-0003-the-incremental-walk.md) proves against a
  real database.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #31, #13 scheduled half)
- An initial import interrupted part-way is **resumed to completion without an administrator having
  to intervene**, and adds no duplicates. Nothing here is aimed at it: `startAll()` already covers
  every marked, active Órgano and the mode rule already answers `RESUMED`, so what this task adds is
  only the tick that arrives without an administrator. Proven by composition — the firing test here,
  over the resumption FEAT-0009 already proves. (SPEC-0005 #14 automatic half)
- An Órgano whose initial import has **not** completed is not treated as up to date by the
  scheduler: it resumes rather than refreshing. Same composition — the mode rule decides, and the
  scheduler is one more trigger that cannot disagree with it. (SPEC-0005 #13)
- While a long-running initial import holds the guard — simulated rather than waited out — the
  scheduler's runs **do not start**, and the tick after it ends claims. (SPEC-0005 #35) That the run
  which finally happens covers **the whole period since each affected Órgano's last successful
  import** is the floor's, proven by [TASK-0003](TASK-0003-the-incremental-walk.md). (SPEC-0005 #45,
  claimed there)
- A refusal is logged and the scheduled task completes normally: nothing propagates to Micronaut's
  handler, and the next tick claims. (SPEC-0005 #32, #35)
- A **mark applied while another import was running** — refused rather than queued — results in that
  Órgano being imported by the next scheduled run **without being marked again**. (SPEC-0005 #33
  next-scheduled-run half, #5 next-scheduled-run half)
- A sweep of a fully loaded catalogue settles **`SUCCEEDED`** having added almost nothing: the
  ordinary nightly outcome reads as a success, because the verdict is read off what failed.
  (SPEC-0005 #31)
- The scheduler declares no executor, and `micronaut.executors` gains no entry. No test asserts the
  scheduler's own thread is virtual — that is the precedent's decision, not this one's.
- `conxugal.contratos-menores.import.schedule` is shipped as `0 0 5 * * *` and is overridable by
  configuration.
- No javadoc in the codebase still calls this scheduler a future one.
