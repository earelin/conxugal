---
feat: FEAT-0016
domain: devops
adrs: []
status: todo
depends_on: [TASK-0007]
---

# The R32 read-latency measurement

A repeatable, committed measurement of the reads R32 names, taken under
[SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) R24's **reference environment
and conditions** — the production deployment, ten concurrent readers — so that the four specs'
numbers stay comparable, which is the reason R32 borrows that environment rather than fixing one of
its own.

> ❗ **The harness this task would extend does not exist yet, and an earlier draft wrote as though it
> did.** `scripts/measure-read-latency.sh` and `docs/measurements/` are
> [FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md)'s
> [TASK-0012](../FEAT-0011-contratos-menores-browsing/TASK-0012-read-latency-measurement-harness.md),
> which is `status: todo`. `scripts/` today holds `actions-lint.sh`, `contract-test.sh`,
> `docs-lint.sh` and `openapi-lint.sh`, and there is no measurements directory.
>
> So **the size of this task depends on a status field**, which is stated here rather than left as a
> conditional inside it:
>
> - **if FEAT-0011's TASK-0012 has landed**, this task adds this family's reads to the existing script
>   and a second file beside its recording place;
> - **if it has not**, this task **builds the shared harness** — the concurrency driver, the timing
>   report, the recording convention — for both families, and FEAT-0011's task then adds its own reads.
>
> Either way there is **one** harness. A second script would drift from the first and produce numbers
> nobody could line up, and comparability across the four specs is the entire reason R32 borrows
> SPEC-0005 R24's reference environment rather than fixing one of its own. Whoever picks this up says
> in the commit which branch they took.

## Scope

- **The Órgano measured is the one holding the most licitacións**, and this is stated because R32
  states it: *"the largest Órgano" no longer denotes* now two families exist, since the largest by
  contratos menores need not be the largest by licitacións, and *"a measurement taken on the wrong one
  would describe neither family's worst case"*. Measured on 2026-08-20 that is SERGAS at 16 798
  procedures, but the script **discovers** it rather than hard-coding it, so a later shift in the data
  does not silently invalidate the number.
- **The reads driven**, each under ten concurrent readers, reporting per-read median and p95:
  - the **first page and its count** of the busiest year of that Órgano, in the **default ordering**;
  - a **deep page** of that same selection;
  - both of those **sorted by amount descending** — the ordering the feature argues cannot be
    index-ordered, and therefore the one this measurement exists to price;
  - **a year's selection narrowed by CPV** — the read with **no contratos menores counterpart**, which
    R32 names as one of the two this family adds;
  - the **year-facet read** behind the section's summary, and the **filter-options read**.
- **R32's second new read — a licitación's page with its lotes and bidders — is named as the next
  feature's** and is not driven here, because it does not exist. It is recorded in the measurement
  file as an outstanding row rather than omitted, so nobody reads the file as complete.
- **No new tooling.** `curl` under a loop and `awk` over `%{time_total}`, in the idiom `scripts/`
  already uses. A load-testing dependency would be a moving part bought for six URLs.
- **The recording place** — a file under `docs/measurements/` beside SPEC-0005's, holding the date,
  the deployment, **what the dataset held at the time** (Órganos with visible licitacións, the Órgano
  measured, its busiest year's visible count, and how many of that year's procedures are awarded), the
  timings, and the outstanding row above. Numbers are **added** over time rather than overwritten, so
  a later run under fuller conditions is comparable with an earlier one.
- **A census beside the timings**: how many stored licitacións R25 withholds for want of an
  interpretable publication date. R25 expects that population to be "negligible, because the source
  publishes its dates in one fixed form" — this is the one query that will say whether it is, and it
  belongs here because this is the task already pointed at production data.

  ❗ **It is a database query, not an API call, and so is the Órgano discovery above.** No read this
  feature builds exposes a withheld licitación — that is the point of withholding — and none exposes
  *which Órgano holds the most*, only whether a given one holds any. So the script needs **direct
  database access** to the deployment alongside its authenticated session, which is a precondition it
  must state and check rather than discover. R25's administrator surface, which would make the census
  an API call, is **unowned** (#36).

**It sets no latency budget and must not.** R32's obligation is to **measure and record**; a threshold
is set only by revising the requirement, exactly as SPEC-0005 R24 says of its own.

**Out of scope:** CI, which has neither the dataset nor the environment R32 names; any optimisation
the numbers might suggest, which belongs to whichever feature meets the problem with the measurement
behind it.

## Acceptance criteria

- The script runs against a deployed instance, given its base URL and an authenticated reader's
  session, **discovers** the Órgano holding the most licitacións and its busiest year, and drives
  every read listed above.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #43, three of its four reads)
- It reports per-read median and p95 under **ten concurrent readers**, and fails loudly when a
  precondition is unmet — no session, no database access, no Órgano with licitacións — rather than recording a number
  taken under the wrong conditions. (SPEC-0008 #43, three of its four reads)
- The measurement file records the timings **with the volume they were taken at**, names the
  deployment and the date, carries the undated-licitación census, and states the licitación-page read
  as **outstanding** pending the R21 feature. (SPEC-0008 #43, three of its four reads)
- Running it twice **appends** rather than overwrites, so two runs are comparable.
- **Acceptance is that it runs and records against whatever production holds on the day it lands** —
  which is what makes this a task at all. R32's fuller conditions need FEAT-0015's remaining tasks and
  weeks of running, and taking the measurement under them is discharged the way R32 says a budget is
  set: by revising the requirement.
- **No latency threshold is asserted anywhere**, and the script fails no build.
