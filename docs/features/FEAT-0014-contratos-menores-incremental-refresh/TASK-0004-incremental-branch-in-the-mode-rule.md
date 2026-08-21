---
feat: FEAT-0014
domain: backend
adrs: [0002, 0010]
status: done
depends_on: [TASK-0003]
---

# The incremental branch in the mode rule

`ImportCoveredOrgano` walks an already-loaded Órgano instead of skipping it. **This is the change
that makes every trigger in the system refresh** — a mark, an administrator's manual sweep, and the
scheduler that does not exist yet — because FEAT-0009 centralised the mode decision precisely so
that no trigger could disagree about it.

The `ImportRunOrgano` description in [`docs/api/openapi.yaml`](../../api/openapi.yaml) is part of
this change rather than housekeeping after it: the contract is authored first and lint-gated under
[ADR-0010](../../architecture/0010-design-first-openapi-contract.md), and it currently tells clients
something this task makes false.

## Scope

- `ImportCoveredOrgano.outcomeFor` routes `INCREMENTAL` to
  [`RefreshOrganoContratosMenores`](TASK-0003-the-incremental-walk.md) with the same
  `stillEligible` supplier the initial walk gets, instead of answering `Settlement.alreadyLoaded()`.
- **One new settlement: a clean refresh is `SUCCEEDED` with no reason.** It joins
  `readItsHistoryOut()` as the only other ending with nothing left to explain. The two endings a
  refresh can otherwise have already read correctly and need no variant: `unmarkedMidWalk()` says
  exactly the right thing, and a lost guard already answers `Optional.empty()`.
- **`reachedTheHistoryFloor()` keeps its single caller**, the initial walk — the only walk that can
  reach a floor. Routing a refresh through `endedOnItsOwnTerms` would make **every successful
  nightly refresh** store, and serve over `GET /api/admin/import-run/{id}`, the sentence *"Read
  every window down to the configured history floor without the stored count matching the
  source's"*.
- **`alreadyLoaded()` disappears**, together with its `ALREADY_LOADED` reason string.
- **Three javadocs stop promising that `INCREMENTAL` is implemented nowhere**, and one of them is in
  the file this task edits:
  - `ImportCoveredOrgano.outcomeFor` — *"a mark, a manual sweep and a future scheduler all resume a
    half-loaded Órgano and all **leave a loaded one alone**"*. The first half stays true and the
    second is exactly what this task reverses;
  - `ContratosMenoresImportMode` — *"`INCREMENTAL` is named and implemented nowhere: it is returned,
    and the orchestrator skips such an Órgano"*;
  - `ImportRunOrganoState` — *"an Órgano already complete is skipped because there is no incremental
    mode to run for it yet"*, which becomes the historical note below.

  The *"future scheduler"* phrasing in `ContratosMenoresImportMode` and `ClaimContratosMenoresImport`
  is **left alone here** — it is still true until [TASK-0006](TASK-0006-the-scheduler.md), which
  corrects it.
- **`ImportRunOrganoState.SKIPPED` loses its only producer and is kept.** Rows written before this
  feature still carry it, and an enum value removed is a stored row that no longer reads. Its
  javadoc and the contract description below become historical rather than current.
- `docs/api/openapi.yaml`: the `ImportRunOrgano` description stops saying an Órgano is *"skipped
  when its history is already complete and there is no incremental mode to run yet"* and says
  instead what `SKIPPED` now is — a state carried by runs recorded before the incremental refresh
  existed. Run `scripts/openapi-lint.sh` before committing.

**Out of scope:** the order a sweep takes its Órganos in
([TASK-0005](TASK-0005-the-sweeps-order.md)); the scheduler
([TASK-0006](TASK-0006-the-scheduler.md)); recording that a run was triggered by a scheduler, which
is [SPEC-0007](../../specs/SPEC-0007-monitor-import-runs.md) R2's and lands with it.

## Acceptance criteria

- An administrator's sweep of a fully loaded catalogue **refreshes** every covered Órgano and
  reports it, instead of skipping it. No `SKIPPED` row is produced by any path.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #29 incremental clause)
- An Órgano marked, unmarked while **complete**, and marked again is **refreshed**: it picks up
  everything published while it was unmarked and re-reads none of the history it holds. Contrast an
  Órgano unmarked while **incomplete**, which is still resumed. (SPEC-0005 #44, #46)
- The four modes stay distinguishable in what they retrieve: initial reaches the earliest
  publication, resumed continues without restarting, **incremental reads only the recent window**,
  and no trigger selects a historical re-read. (SPEC-0005 #47 incremental half)
- A clean refresh settles the Órgano `SUCCEEDED` with **no reason at all** — not
  `reachedTheHistoryFloor()`'s sentence, and not any variant of it. (SPEC-0005 #29)
- A refresh cut off by an unmark settles `STOPPED` with the existing mid-walk reason; one cut off by
  the guard going settles **nothing** and stops the run. (SPEC-0005 #8, #32)
- A refresh whose T₁ write fails settles the Órgano `FAILED`, the run carries on to the Órganos
  after it, and everything the refresh stored stands. (SPEC-0005 #36's *"contracts already stored
  are unchanged"* and *"the remaining marked Órganos are still imported"* halves; the criterion's
  own trigger is an unreachable source, and this is a database failure with the same consequences.)
- `openapi.yaml` no longer claims there is no incremental mode, and `scripts/openapi-lint.sh`
  passes.
- `ImportRunOrganoState.SKIPPED` still exists and a stored row carrying it still deserialises and is
  reported by `GET /api/admin/import-run/{id}`.
