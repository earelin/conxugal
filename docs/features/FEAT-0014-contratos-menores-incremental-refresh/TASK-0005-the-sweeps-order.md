---
feat: FEAT-0014
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0004]
---

# The sweep's order: cheap work first

`ExecuteContratosMenoresImport` orders the coverage it reads back by each Órgano's current mode —
**incremental, then resumed, then initial**. This discharges the prioritisation
[SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) R22 explicitly leaves to a
feature: *"Prioritising which import runs first when several are due is left to the feature; this
requirement fixes only that they do not overlap."* This is the feature where several are routinely
due, and [TASK-0006](TASK-0006-the-scheduler.md) is what makes that a nightly fact rather than an
edge case.

**What goes wrong without it.** `claimAll()` enumerates every eligible Órgano — `NEVER_STARTED` ones
included — and the run walks them in whatever order the catalogue read returned. One Órgano marked
while the guard was held lands in the next sweep as an **initial** import; near the front of that
list it holds the whole sweep for days while every other Órgano's refresh waits behind it and every
subsequent nightly tick is refused by the guard. Nothing is lost — the floor absorbs it — but a
catalogue's freshness would be hostage to the position of one row.

## Scope

- The covered list is **sorted once, when the run starts to execute**, before the first Órgano is
  walked. The key is `ContratosMenoresImportMode.of(organo.importStatus())` — the same rule
  `ImportCoveredOrgano` applies, over the same fact.
- **This adds a collaborator and a read, and both are worth stating plainly.**
  `ExecuteContratosMenoresImport` takes `ImportRunRepository` and `ImportCoveredOrgano` today, and
  `coveredOrganosOf` reads the *run record*, which yields identities and nothing else. Sorting by
  mode therefore means **`OrganoRepository` becomes a third collaborator**, and the covered Órganos
  are read from the catalogue **once up front** in addition to the per-Órgano `findById` that
  `ImportCoveredOrgano` will keep doing when each Órgano's turn comes.
  - The cost is one query per covered Órgano at the head of a run — `importState` is a
    `@Relation(ONE_TO_ONE)`, so the status arrives with the row rather than as a second read.
    Against a sweep measured in hours or days, four hundred reads are not worth removing, and
    removing them would mean threading loaded aggregates from here into `ImportCoveredOrgano` and
    making that class trust a catalogue row read before the run began — which is exactly the
    staleness its own re-read of eligibility exists to defeat.
  - So the duplicate read is **accepted deliberately**, not overlooked. If a sweep of a large
    catalogue ever shows it mattering, the fix is a projection of `(organoId, importStatus)` rather
    than a shared aggregate.
- **Nothing is stored on the run.** No column, no ordering field, no recorded mode. That keeps
  FEAT-0009's *no column on the run record* rule intact, and re-deriving is stable: the only mode
  change a run can produce is its own `INCOMPLETE → COMPLETE`, and the guard means nothing else is
  moving.
- **An Órgano that has vanished from the catalogue sorts with the cheapest**, ahead of the
  incremental ones. The up-front read answers nothing for it, so it has no mode; it is settled
  `FAILED` by `Settlement.goneFromTheCatalogue()` without walking anything, and it costs no
  measurable time — so a sweep gets its no-op rows out of the way rather than writing them after a
  multi-day load. An Órgano *unmarked* between the claim and its turn is a different case and needs
  no special slot: it still has a status, so it sorts by mode and is settled without a walk when its
  turn comes.
- **It is not a queue and not a priority scheme.** Every covered Órgano is still walked, still
  serially, still exactly once; only the order changes. R22's serialisation and the run's
  reportability are untouched, as is the covered list itself — the same Órganos, reported the same
  way.
- The ordering is **total and stable**: within a mode the covered list keeps the order the run
  recorded, so a run's per-Órgano outcomes stay in a well-defined sequence.

**Out of scope:** which Órganos are eligible (`ClaimContratosMenoresImport`'s), which mode each
takes (the per-Órgano state's), and whether an import may start at all (the guard's).

## Acceptance criteria

**This task claims no spec criterion of its own** — it discharges R22's delegated prioritisation.
The SPEC-0005 numbers below are properties it must not break, not criteria it proves.

- A run covering an initial, a resumed and an incremental Órgano walks them **incremental, resumed,
  initial** whatever order the run recorded them in. (R22's delegated prioritisation)
- A newly marked Órgano sharing a sweep with loaded ones is walked **last**: the loaded ones refresh
  first and the multi-day load runs after them. Both are still covered by the one run, and both
  still appear in its per-Órgano outcomes. (R22's delegated prioritisation)
- An Órgano missing from the catalogue is settled `FAILED` **before** any walk begins, and the run
  carries on. (R22's delegated prioritisation)
- Órganos are still walked one at a time, each finished before the next begins, and the run's
  verdict is unchanged for any given set of outcomes: ordering changes when an outcome is produced,
  never which one it is. (SPEC-0005 #32, #30)
- The `import_run` and `import_run_organo` schemas are unchanged: this task adds no column and
  writes no ordering anywhere.
- The covered list is read from the run, not looked up again, so a sweep taking days still covers
  exactly what it was claimed for. The catalogue read added here decides **order only** — it never
  adds an Órgano to the coverage, and never drops one.
- `ImportCoveredOrgano` is unchanged: it still reads each Órgano when that Órgano's turn comes, so
  an Órgano unmarked after the sort but before its turn is still settled `STOPPED` with nothing
  read. (SPEC-0005 #8)
