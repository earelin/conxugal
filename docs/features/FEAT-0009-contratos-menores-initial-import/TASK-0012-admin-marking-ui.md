---
feat: FEAT-0009
domain: frontend
adrs: [0003, 0004, 0015]
status: todo
depends_on: [TASK-0002, TASK-0006, TASK-0011]
---

# Admin marking UI

The only user interface this feature adds: the mark control, its indicator, and the outcome of
the run a mark or a trigger starts — all **inside FEAT-0007's admin Órganos section**. No new
screen, no new route, and no contract browsed. Governed by
[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (Vite + Mantine),
[ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md) and
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)
(feature slices with a shared core).

Visual target: the mockup set in [`design/`](design/README.md) — four screens, whose README also
records what they draw but deliberately do not build.

**Prerequisites outside this feature.** Two FEAT-0007 tasks, both `todo`:
[TASK-0007](../FEAT-0007-organos-taxonomia-classification/TASK-0007-organos-section-and-tree-view.md)
builds the Órganos section, its slice at `ui/src/features/organos/`, its table, its toolbar and
the `ProblemError` this task keys refusals on; and
[TASK-0010](../FEAT-0007-organos-taxonomia-classification/TASK-0010-import-trigger-ui.md) builds
the `Importar catálogo` trigger and the banner slot this feature's outcome sits beside — the
second trigger and the "both disabled while either import runs" rule below have nothing to be a
peer of without it. This task adds into that structure rather than inventing a container.

## Scope
- **One column, not a screen.** The term-Órganos table gains `CONTRATOS MENORES` between
  `ESTADO` and `ACCIÓNS`; `ACCIÓNS` keeps `Quitar do termo` untouched. The count caption under
  the table gains the marked tally — the *listed as marked* half of #4 — derived **client-side**
  from the catalogue read, not from a new endpoint
  ([`organos-import-mark.svg`](design/organos-import-mark.svg)).
- **The section reads `GET /api/admin/organos`** instead of `GET /api/organos`: it is the read
  that carries `importado` and the import state, and swapping it keeps the section on one
  request rather than two. The taxonomy read is unchanged.
- **The control is a `Switch`** — the mark is a durable attribute of the Órgano, not a one-off
  action, and it maps 1:1 onto `PUT` and `DELETE /api/admin/organo/{id}/importado`. Being
  icon-only it carries an `aria-label`. Turning it on opens a confirmation naming what the mark
  costs — days of walking, every other import refused meanwhile
  ([`mark-organo.svg`](design/mark-organo.svg)). R4 does not require that dialog; it is a design
  decision, because `Marcar e importar` is the honest name for what the `PUT` does.
- **The badge vocabulary is the three-state rule made visible**, read from the mark and the
  import state the admin catalogue read carries — five row states, four badges:

  | Mark | Import state | Badge |
  | --- | --- | --- |
  | marked | never started | `MARCADO` |
  | marked | incomplete | `PARCIAL` |
  | marked | complete | `IMPORTADO` |
  | unmarked | incomplete or complete | **`SEN ACTUALIZAR`** |
  | unmarked | never started | dimmed `—` |

  Two badges would let a half-loaded Órgano read as up to date on screen, which is the defect R8
  names. `SEN ACTUALIZAR` exists because R5 **keeps** an unmarked Órgano's contracts: a dash
  there would render an Órgano holding a million rows identically to one never touched, and #7
  asks the surface to say it is no longer being updated. It is neutral, never red — unmarked is
  a decision, not a fault — and its tooltip names which stored state it holds, so *resumable*
  versus *complete* stays reachable without a fifth badge. All six row states — the five above
  plus the disabled inactive row — are drawn in
  [`mark-states.svg`](design/mark-states.svg).
- **An inactive Órgano keeps its row**, dimmed, with the switch **disabled and explaining
  itself** — never hidden and never red: inactive is inert, not an error.
- **The mark's response is not a plain acknowledgement.** `PUT` answers with the run identifier
  when an import started or with the refusal reason when none did, and the UI must render both —
  the refusal **neutrally**, stating that the mark was kept and the import was what was refused,
  and that no scheduled run recovers it until the incremental feature lands.
- **The outcome banner sits where FEAT-0007's import feedback already lives**, reading
  `GET /api/admin/import-run/{id}` on demand: in progress, succeeded, **partially succeeded**,
  failed, **abandoned** — a multi-day import whose process died, which the run read reports and
  which the mockups predate — and the two refusals, guard-held and not-eligible, rendered
  neutral and with no counts, because a refusal is an outcome and not an error
  ([`import-run-outcome.svg`](design/import-run-outcome.svg)). `Actualizar` re-reads the one run;
  a *consultado hai …* line lets a reader tell fresh from stale without polling.
- **A second trigger beside the catalogue's:** `Importar contratos menores`, the outline peer of
  FEAT-0007's filled `Importar catálogo`, calling `POST /api/admin/contratos-menores/import`.
  While either import runs, **both** are disabled with the guard as the stated reason — R22's
  shipped cost, drawn rather than discovered.
- Refusals are keyed on the problem `type` through the slice's `ProblemError`, **never on the
  status**: both refusals are `409`, and telling them apart is #34.
- **Two absences are deliberate.** No progress indicator of any kind — that is
  [SPEC-0007](../../specs/SPEC-0007-monitor-import-runs.md)'s. And because the only run read is
  by identifier, the banner is bound to the run triggered in that session: a reload loses it, and
  a persistent *última importación* caption would need a read no endpoint here offers — the same
  gap FEAT-0007's design recorded for the catalogue import.
- All copy is Galician and lives in the slice's strings module, not inline
  ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC7). Usable at a 360 px viewport (SPEC-0001 AC6).

## Acceptance criteria
- An `ADMIN` can mark an Órgano through the switch, see it marked in the table and counted in the
  caption's tally, and unmark it again; a newly discovered Órgano shows unmarked with a dimmed
  `—`. ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #4)
- The three import states render as three distinct badges: an Órgano marked but never started,
  one half-loaded, and one fully imported are visibly different, and the half-loaded one never
  reads as up to date. (SPEC-0005 #46)
- An Órgano holding contracts and **no longer marked** shows `SEN ACTUALIZAR`, visibly distinct
  from the dimmed `—` of an Órgano with nothing stored, and neither is styled as an error; the
  tooltip states whether what is stored is partial or complete. (SPEC-0005 #7, UI half of the
  *no longer being updated* clause)
- An inactive Órgano's row is present, dimmed, with the switch disabled and its reason reachable
  — not hidden and not styled as an error. (R3's ineligibility made visible; #3 itself is
  proven by [TASK-0010](TASK-0010-multi-organo-orchestration.md), which runs an import)
- Marking while no import runs shows the run's outcome banner; marking while one runs shows the
  **guard refusal**, neutrally, and the row stays marked afterwards. (SPEC-0005 #33
  refused-and-kept half; #5's immediate-import half is
  [TASK-0011](TASK-0011-triggers-and-run-read.md)'s, and its *records the initial import as
  complete* clause is TASK-0009's)
- All seven banner states render distinguishably — in progress, succeeded, partially succeeded,
  failed, abandoned, guard-held, not-eligible — with the two refusals carrying no counts and
  neither styled as a failure. (SPEC-0005 #29 initial/resumed modes only, #30, #32 guard half,
  #34)
- The two refusals are told apart by problem `type`, proven by two `409` responses differing only
  in their type rendering different messages. (SPEC-0005 #34)
- Both toolbar triggers are disabled, with the guard named as the reason, while an import is
  known to be running. (SPEC-0005 #32)
- All added copy is in Galician and the section is usable at 360 px with the extra column.
  (SPEC-0001 AC6, AC7)
- Component-tested with HTTP mocked at the network boundary per the project's `nock` convention:
  the mark and unmark flows including the confirmation, each badge state, the disabled inactive
  row, every banner state, and both refusals.
