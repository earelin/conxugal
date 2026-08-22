---
feat: FEAT-0015
domain: backend
adrs: [0008, 0010, 0017, 0021]
status: done
depends_on: []
---

# Per-family run coverage, `Importer`, and the published contract

R27 requires that marking an Órgano imports **both** contract families "within one run", and #38
requires the outcome to name what each Órgano's import did. The shipped run record cannot express
either: `import_run_organo`'s primary key is `(run_id, organo_id)` and its own migration comment
says the intent plainly — *"no run can cover an Órgano twice"*.

This task makes a coverage row **per Órgano per family**, and carries the change out to the
published contract, because the run read is a `GET` an administrator's browser already calls.
Nothing licitacións-specific is imported here: this is the record the later tasks write into.

Governed by [ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md), whose
**single-insertion-path** property the change must preserve — `claim` stays the only place a run row
is written, so a second family cannot become a second claim against a guard the first one holds.
Authored contract-first under
[ADR-0010](../../architecture/0010-design-first-openapi-contract.md) and gated by
[ADR-0021](../../architecture/0021-openapi-contract-testing-with-schemathesis.md).

**The run-level `added` and `refreshed` are kept.** An earlier draft of this task dropped them, on
the reasoning that the coverage row already holds a pair. That is true only of a run that *has*
coverage rows, and the catalogue import has none: `ImportOrganos` claims with `List.of()` and
settles through `complete(runId, verdict, added, refreshed)`, and
`ImportRunRepository.complete`'s own contract says why — *"An importer covering no Órganos has no
other way to record a count: an advance moves a coverage row first and leaves the run's totals alone
when there is none to move."* Dropping the columns would leave FEAT-0006's shipped 03:00 job
recording its counts nowhere, silently. So this change is **additive to the published contract**,
and #38's per-family question is answered from the coverage rows rather than from the run.

## Scope

- **A migration** (next free `V` across `db/migration` **and** `db/migration-local` — taken at merge
  time, since three sibling tasks also add one and the dependency graph does not order them):
  - `import_run_organo` gains **`family TEXT NOT NULL`**, and its primary key becomes
    `(run_id, organo_id, family)`;
  - existing rows are **backfilled to `CONTRATOS_MENORES`** before the key is re-keyed. Every run
    recorded to date is a contratos menores or catalogue run, and a catalogue run covers no
    Órganos, so the backfill is total and unambiguous.
- **A new `ContractFamily` enum** in the domain — `CONTRATOS_MENORES` and `LICITACIONS` — for the
  coverage column. **Deliberately not `Importer`**: `Importer.ORGANOS` and the new `AMBAS_FAMILIAS`
  are both nonsense in a coverage row, and a column whose type admits values it can never hold is a
  column every reader has to be told about.
- **`Importer` gains `LICITACIONS` and `AMBAS_FAMILIAS`.** Named so rather than `CONTRATOS`, which
  reads as a superset of `CONTRATOS_MENORES` by name alone. The run-level column keeps its meaning —
  *what was triggered* — and is not derived from the coverage.
- **The three port methods that address a coverage row take the family**: `claim` takes (Órgano,
  family) pairs rather than Órganos, so the coverage is still enumerated up front in one insertion
  and ADR-0017's property is untouched; `advance` and `finishOrgano` take it because there are now
  two rows per Órgano and addressing one by the old two-column key would update whichever the
  database returned first.
- **`ImportRunOrganoCoverage` carries the family**; `ImportRunReport` does **not** — a run spans
  two, and the family belongs on the row that has one.
- **The four shipped call sites** thread it through: `ClaimContratosMenoresImport` and
  `ImportOrganos` (both call `claim`), `ReadContratosMenoresWindow` (`advance`) and
  `ImportCoveredOrgano` (`finishOrgano`). `ExecuteContratosMenoresImport`'s covered-Órganos read
  filters the run's coverage to its own family rather than taking every row.
- **The two application DTOs**, `ImportRunResponse` and `ImportRunOrganoResponse`, without which the
  `GET` criterion below is unreachable.
- **`openapi.yaml`:** `ImportRunOrgano` gains a required **`family`**; `ImportRun.importer`'s enum
  gains `LICITACIONS` and `AMBAS_FAMILIAS`; and the `added`/`refreshed` descriptions on **both**
  schemas stop saying "contratos menores", which a second family falsifies. Nothing is removed.

**Out of scope:** any licitacións import, any new endpoint, and any change to what a contratos
menores trigger does — it still claims one family, and its coverage is still one row per Órgano.

## Acceptance criteria

- A run claiming `[(A, CONTRATOS_MENORES), (A, LICITACIONS)]` records **two** coverage rows for
  Órgano A, each with its own state, counts and failure reason, and the run reads back with both.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #38)
- **A catalogue run still records its counts.** `ImportOrganos` covers no Órganos, settles through
  `complete`, and its `added`/`refreshed` read back off the run exactly as before the migration.
  This is the regression the earlier draft of this task would have shipped, so it is asserted
  rather than assumed.
- `advance` and `finishOrgano` against `(run, A, LICITACIONS)` leave `(run, A, CONTRATOS_MENORES)`
  untouched — verified against PostgreSQL (Testcontainers).
- A contratos menores run over N Órganos still records N coverage rows, all `CONTRATOS_MENORES`, and
  `ExecuteContratosMenoresImport` walks exactly those. *A regression guard, not a trace: #38 is a
  licitacións criterion and this is the shipped family.*
- Migrating a database holding runs recorded before this change leaves every existing coverage row
  readable, with `family = 'CONTRATOS_MENORES'`, and the run read answers for them unchanged.
- `claim` is still the only path that writes an `import_run` row, and `ImportRunArchTest` passes.
  **The coverage insert is not covered by that test** — it is raw SQL in the private
  `enumerateCoverage`, which no existing rule sees — so this task adds a rule over it, since
  re-keying the coverage is exactly the change that makes a second coverage-insertion path
  tempting. ([ADR-0017](../../architecture/0017-import-run-state-in-postgresql.md))
- `GET /api/admin/import-run/{id}` answers a body carrying `family` on every covered Órgano, and
  the ADR-0021 conformance run passes against the amended `openapi.yaml`.
- `scripts/openapi-lint.sh` passes.
