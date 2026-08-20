---
feat: FEAT-0014
domain: backend
adrs: [0002]
status: done
depends_on: []
---

# Extract the shared window read

The middle of a walk — the part an initial import and an incremental refresh do identically — moves
out of `ImportOrganoContratosMenores` into a collaborator both can use. **Behaviour-preserving**:
this task adds no capability, changes no output and touches no schema. The existing unit and
integration tests are the safety net, and they must pass unchanged.

**Why an extraction rather than a second copy** is the feature's *The walk, and the middle the two
walks share*. The page loop asks the guard **twice** — once before fetching, and once *after* the
batch commits and *before* the progress write, because the progress write renews the run's own
last-advanced stamp and a walk that asked only before would be reading a liveness it had just
written itself — and asks eligibility **once, at the very bottom**, so a withdrawn mark cannot
leave a batch's contracts committed while its counts are not. That order is documented at length in
the class precisely because it is easy to get wrong, and a second copy of it is the likeliest way
this feature introduces a defect in the *first* import.

The alternative — one class taking a start point and an ending predicate — was rejected in the
feature: the two walks differ in what they **do** at each end, not only in where the ends are.

## Scope

- A collaborator in `gal.conxugal.domain.contrato` owning **one window, paged to exhaustion**:
  - both `importRuns.holdsGuard(runId)` asks, in their documented positions, answering
    `GUARD_LOST`;
  - the source fetch at `PAGE_SIZE`, and the short-page test that ends the window;
  - `batch.store(...)` and the added/refreshed tally;
  - `importRuns.advance(...)`, inside the try/catch that keeps a bookkeeping failure from breaking
    the import;
  - the `stillEligible` ask at the bottom, answering `UNMARKED`;
  - the window's outcome: what it stored, the `recordsTotal` the source reported, and what cut it
    off if anything did.
  - `PAGE_SIZE` moves here with the fetch it bounds. `WINDOW_DAYS` does **not**: it is the step of a
    walk, not a property of one window, and both walks read it from
    `ImportOrganoContratosMenores` — package-private in the package they all share.
- **One caller-supplied hook, invoked immediately before the advance and inside the same
  try/catch**, for whatever else that walk records per batch. Being inside it is not incidental:
  `recordProgress` today wraps `updateCursorDate` and `importRuns.advance` together, so a failed
  cursor write is logged and the walk carries on. A hook outside the catch would start breaking the
  *initial* import on a transient bookkeeping failure — the defect this whole task exists to
  prevent.
- **The hook takes the batch's counts and the window's bounds, plus whether that page exhausted the
  window** — everything `recordProgress` reads today. The cursor value is
  `lastPage ? windowStart : windowEnd`, and `lastPage` is the loop's own state: a hook that could
  not see it would silently drop the *"the cursor stays at the window's end until the window is
  exhausted"* conservatism that the class documents, and a resumption from mid-window would skip
  the rest of it.
- `ImportOrganoContratosMenores` passes the **cursor write** as its hook; nothing else moves with
  it. `recordProgress` is two writes today and only `importRuns.advance` is shared — moving both
  would hand `cursor_date` a second writer, and its single-writer discipline is what makes
  resumption of an interrupted initial import correct.
- **`StopReason` is promoted** out of `ContratosMenoresImportSummary` into a type of its own in the
  same package, and every reference follows. Being cut off by an unmark or by the guard going is a
  property of *any* walk and is what the shared reader answers; nesting it inside one walk's summary
  was right when there was one walk.
- `ImportOrganoContratosMenores` keeps everything that is its own: the resume point, the 89-day
  step, the history floor, the `recordsTotal` test, the `COMPLETE` mark and the summary it answers.

**Out of scope:** any change to what an import stores, records or reports; the incremental walk
itself ([TASK-0003](TASK-0003-the-incremental-walk.md) is its only other caller, and it does not
exist yet).

## Acceptance criteria

- No new criterion. This task must leave
  [SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) **#14**, **#17** and **#32**
  exactly as they were, and the existing tests of `ImportOrganoContratosMenores` — unit, and
  `OrganoContratosMenoresImportIntegrationTest` — pass with **no change beyond `StopReason` being
  requalified**. `ContratosMenoresImportSummaryTest` names it qualified today
  (`ContratosMenoresImportSummary.StopReason.UNMARKED`), so it is requalified rather than
  re-imported; no assertion in any of them changes.
- The guard is still asked twice a page, and the second ask still sits between the batch commit and
  the progress write: a test in which the guard is lost only after the batch commits still ends the
  walk with nothing advanced. (SPEC-0005 #32)
- Eligibility is still asked once a page, at the bottom: an Órgano unmarked during a window's last
  page still ends `UNMARKED`, and the walk does **not** go on to test the stored count and mark it
  complete. (SPEC-0005 #8)
- `cursor_date` has exactly one writer. The shared reader neither imports
  `ContratosMenoresImportStateRepository` nor writes a cursor; the initial walk's hook does.
- A bookkeeping failure still costs the import nothing: an `advance` **or a hook** that throws is
  logged and the walk carries on with its contracts committed and unchanged. (SPEC-0005 #36's
  *"contracts already stored are unchanged"* half)
- The cursor is still written conservatively: within a window it stays at the window's end, and only
  the page that exhausts the window moves it back. (SPEC-0005 #14)
- `StopReason` is a top-level type in `gal.conxugal.domain.contrato`, and
  `ContratosMenoresImportSummary` still answers one.
