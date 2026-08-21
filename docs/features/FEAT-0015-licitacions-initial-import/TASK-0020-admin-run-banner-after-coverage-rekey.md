---
feat: FEAT-0015
domain: frontend
adrs: [0004, 0015, 0018]
status: todo
depends_on: [TASK-0001]
---

# The admin run banner, after the coverage re-key

[TASK-0001](TASK-0001-per-family-run-coverage.md) makes a run over N Órganos return **2N** coverage
entries, and `ui/src/features/organos/imports/importRunOutcome.ts` counts that array directly for
"N Órganos covered". Left alone the banner says *"6 órganos cubertos"* for three Órganos — plausible
enough to survive a glance, which is why this task exists.

**It depends on task 1 alone, and should land with it or immediately after.** An earlier draft also
depended on TASK-0018, eighteen commits away down the critical path, which would have parked the fix
for a deliberate break behind the entire feature. Nothing here needs it: every criterion below is
fixture-driven, and the `family` field, the two new `importer` values and the stub all come from
task 1.

A read-side edit inside the admin Órganos slice, on
[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md)'s stack and
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)'s
feature-based layout. No route, no new API call, no new component.

## Scope

- **`ui/src/features/organos/imports/contratosMenores.ts`:**
  - `ImportRunOrgano` gains **`family: 'CONTRATOS_MENORES' | 'LICITACIONS'`**;
  - `ImportRun.importer` gains `'LICITACIONS'` and `'AMBAS_FAMILIAS'`.

  `ImportRun.added` and `ImportRun.refreshed` **stay**. Task 1 keeps them — they are the catalogue
  import's only count channel — so `contractCounts` keeps working unchanged, and this task is
  smaller than an earlier draft made it.
- **`ui/src/features/organos/imports/importRunOutcome.ts`:**
  - `completedOf` and the covered/scope counts **group by Órgano** — a distinct count of
    `organoId`, not the array's length — so three Órganos read as three;
  - an Órgano counts as completed when **every** family row for it succeeded. A mixed Órgano is not
    completed, which is the same rule the run's own `PARTIALLY_SUCCEEDED` verdict follows;
  - `failureLines` **collapses an Órgano that failed in both families into one entry**, because
    `copy.run.failedOrganos(failures.length)` renders *"Fallaron N órganos"* and would otherwise
    count one Órgano twice. That means `RunFailure.line` must carry both reasons: the entry keeps
    its single `organoId` and joins the two reasons with the existing `·` separator, so no new
    string is needed in `strings.ts`.
- **The counts stay family-agnostic in the copy.** `strings.ts` says *"contratos engadidos"*, which
  a licitacións run makes imprecise. Left alone deliberately: a per-family breakdown is a design
  decision with no mockup, not a rename. If the summed figure proves confusing in use, that is a
  browsing-side question with real usage behind it.
- **`ui/wiremock/mappings/contratos-menores.json`** — the run stub gains `family` on every coverage
  entry. It is the shared local API stub
  ([ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)), so
  **dev and preview** read it and a stub answering the old shape would show developers a contract
  the server no longer serves.

  *No SPA acceptance spec reads `/api/admin/import-run`* — `admin-organos.spec.ts` only asserts the
  mark switch is disabled, so the run banner never renders there. An earlier draft claimed the
  acceptance tests depended on this stub and set a criterion about counts they do not assert.
- **`importRunOutcome.test.ts` and `ContratosMenoresImport.test.tsx`** move with it, and gain the
  two-family case: three Órganos, six coverage rows.

**Out of scope:** any licitacións trigger in the UI — the mark already starts both families
([TASK-0018](TASK-0018-start-marked-organo-import.md)) and R3 is satisfied by **reusing** the mark
rather than adding a second control. The mark's own copy is
[TASK-0025](TASK-0025-the-marks-copy-now-that-it-means-both-families.md)'s. Any per-Órgano family
badge, and everything in R19–R26, is the browsing feature's.

## Acceptance criteria

- A run over **three** Órganos with **six** coverage rows renders *"3 órganos cubertos"*, not 6.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #38 display half)
- An Órgano whose contratos menores row succeeded and whose licitacións row failed counts as **not**
  completed. (SPEC-0008 #38 display half)
- An Órgano failing in **both** families appears **once** in the failure list, with both reasons
  legible in its line, and *"Fallaron 1 órgano"* above it — not 2.
- A single-family run renders exactly as it does today: same counts, same failure lines, same
  contract totals. *The regression guard for a change whose whole risk is the shipped case.*
- A run whose `importer` is `AMBAS_FAMILIAS` renders without falling to the unknown-verdict branch —
  that branch is keyed on the run's `state`, and this asserts the new importer value does not leak
  into it.
- The WireMock run stub answers the new shape and validates against the amended `ImportRunOrgano`
  schema.
- `npm run lint`, `npm run test` and `npm run build` (which type-checks) pass in `ui/`.
