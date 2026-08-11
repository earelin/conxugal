---
feat: FEAT-0011
domain: devops
adrs: []
status: todo
depends_on: [TASK-0007, TASK-0010]
---

# The R24 measurement harness, and the place its numbers are recorded

A repeatable, committed measurement of the reads R24 names, run against the **production
deployment** — the reference environment R24 fixes, and the one SPEC-0006 and SPEC-0007 will
measure on too, so all three sets of numbers are comparable.

**The measurement itself is an obligation, not this task.** R24's conditions — ten imported
Órganos including the largest, under ten concurrent readers — cannot be created by any task in
this feature: they need FEAT-0009's remaining tasks, the incremental feature, and weeks of
running. So this task delivers the **method** and the **recording place**, and its acceptance is
that it runs and records against whatever production holds on the day it lands. Taking the
measurement under R24's full conditions is discharged the way R24 says a budget is set — by
revising the requirement.

**It sets no latency budget and must not.** R24's obligation is to measure and record; a
threshold is still set only by revising it.

## Scope

- **A committed script**, `scripts/measure-read-latency.sh`, run manually against a deployed
  instance — **not in CI**, which has neither the dataset nor the environment R24 names. It takes
  the instance's base URL, a session for an authenticated reader, and the Órgano and year to
  measure, and it drives:
  - the **first page and its count** of the busiest year of the largest Órgano;
  - a **deep page** of that same selection;
  - **both of those sorted by amount descending** — the read R24 names as the one that actually
    breaks;
  - the **year-facet read** behind the section's summary.
  Each under **ten concurrent readers**, reporting per-read median and p95 wall time.
- **No new tooling.** Ten `curl` loops and `awk` over `%{time_total}` is the whole harness, in the
  bash-script idiom `scripts/` already uses. A load-testing dependency would be a moving part
  bought for four URLs.
- **The withheld-contract census, beside the timings**: one SQL statement over the same production
  dataset, counting stored contratos menores that R28 withholds **split by which of the three
  values they are missing** — date, amount, awardee, and combinations. It is the only evidence
  that will say whether R28 withholds a handful of rows or a large fraction of the dataset, and it
  belongs here because this is the one task already pointed at production data.
  - The awardee is the cause most likely to withhold at scale — an unusable fiscal identifier is
    common enough that SPEC-0006 R5 exists to define it — and the one whose anomalies **do not
    clear themselves**. [TASK-0003](TASK-0003-paged-ordered-counted-reads.md)'s tests prove the
    withholding works; they cannot say how much it withholds.
- **The recording place**: `docs/measurements/SPEC-0005-R24-read-latency.md`, holding the date, the
  deployment measured, what the dataset held at the time — number of imported Órganos, the largest
  Órgano and its busiest year's visible count — the timings, and the census. Numbers are **added**
  to it over time rather than overwritten, so a later run under fuller conditions is comparable
  with an earlier one. It sits outside the feature folder because SPEC-0006 and SPEC-0007 will
  record on the same environment and the point is comparability.
- **What it measures is the indexed implementation** that
  [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md) and
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) built, so what it records is the design's
  real latency rather than one deliberately left slow.
- The script documents its own preconditions and fails loudly when they are unmet — no session, no
  such Órgano, a year the Órgano has no visible contracts in — rather than recording a fast zero.

## Acceptance criteria

- `scripts/measure-read-latency.sh` runs against a deployed instance and produces, for each of the
  five reads, a median and p95 under ten concurrent readers.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #37, method half)
- The same run produces the count of withheld contratos menores split by which of the publication
  date, the amount and the awardee is missing. (SPEC-0005 #52 evidence only — the administrator's
  view of those anomalies is **not** built here and remains unowned)
- `docs/measurements/SPEC-0005-R24-read-latency.md` exists and holds one recorded run: the date,
  the deployment, the dataset conditions as they actually were, the timings and the census — with
  the conditions stated honestly, including where they fall short of R24's ten Órganos and largest
  Órgano.
- The document states **no threshold, budget or pass mark**, and the script asserts none — a run is
  never *failed*. (SPEC-0005 #37)
- The script adds no dependency beyond what a deployment host already has, and is not wired into
  any CI workflow.
- `scripts/docs-lint.sh` passes.
