---
feat: FEAT-0014
domain: frontend
adrs: [0004, 0015]
status: todo
depends_on: [TASK-0006]
---

# The admin copy the scheduler makes true

Five strings in `ui/src/shared/lib/strings.ts` are the **opposite** of what
[TASK-0006](TASK-0006-the-scheduler.md) ships. They are not documentation housekeeping: a
requirement contradicted by the interface that reports it is not met in practice, and an
administrator reads this copy instead of the requirement. `strings.ts` is where the copy lives by
`ui/CLAUDE.md`'s i18n-seam convention — *"all user-facing text (Galician) lives in one `strings`
object rather than scattered through components"* — under
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md).

A copy-only edit inside the admin Órganos slice, on
[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md)'s stack. No component, no route, no API
call changes.

## Scope

Five strings, all under the admin Órganos copy:

| Key | Says today | Must say |
| --- | --- | --- |
| `run.succeededNote` | *"Ata que exista o refresco periódico, estes órganos non se actualizan sós."* | The daily refresh returns to these Órganos and picks up what has been published since. |
| `run.failedNote` | *"…volver disparala retoma onde quedou."* | What was stored stands, and the **next scheduled run** retakes it — triggering it by hand only brings it forward. |
| `run.abandonedNote` | *"…volver disparala retoma onde quedou."* | Same correction: the process stopped without writing its ending, and the schedule is what recovers it. |
| `refusal.guardNote` | *"Mentres non exista o refresco periódico, ningún proceso a retoma: hai que volver disparala."* | The refusal costs freshness, not data: the next scheduled run covers this Órgano without it being marked again. |
| `badge tooltip.imported` | *"Está todo o historial que a fonte publicaba no momento da importación."* | The history is loaded **and kept current**, rather than frozen at the moment of import. |

Proposed Galician, to be settled against the `frontend-design` skill's copy conventions:

- `succeededNote`: *"O refresco diario volve a estes órganos e incorpora o publicado desde a última
  execución."*
- `failedNote`: *"Os contratos xa gardados consérvanse; a seguinte execución programada retoma onde
  quedou."*
- `abandonedNote`: *"O proceso deixou de avanzar e nunca escribiu o seu remate. Os contratos xa
  gardados consérvanse; a seguinte execución programada retoma onde quedou."*
- `guardNote`: *"A seguinte execución programada cobre este órgano sen ter que volver marcalo."*
- `tooltip.imported`: *"Está todo o historial publicado pola fonte, e o refresco diario incorpora as
  novidades e as correccións recentes."*

**Deliberately unchanged:**

- `tooltip.stalePartial` and `tooltip.staleComplete`, which say that *no run returns to this
  Órgano*. That stays true — `markState` answers `stale` only for an Órgano that is active and
  **not** marked, and the scheduler covers only Órganos that are active **and** marked.
- `tooltip.partial` (*"a seguinte importación retoma desde aí"*), which stays true but no longer
  says the resumption is **automatic** — which is what TASK-0006 adds, and R9's automatic half.
  Considered and left: the tooltip's job is to explain what the badge means, and *what* is stored is
  what it explains; *who* resumes it is the run banner's, which this task corrects.

**Out of scope:**

- Any *última actualización* caption beside the `Importado` badge. It needs a per-Órgano *last
  refreshed* read that no endpoint offers, and
  [SPEC-0007](../../specs/SPEC-0007-monitor-import-runs.md) R15 is where Órgano-side import facts
  are surfaced.
- The design SVGs under `docs/features/FEAT-0009-contratos-menores-initial-import/design/`, which
  carry the old wording as a record of what that feature shipped.

## Acceptance criteria

- No string in `ui/src/shared/lib/strings.ts` tells an administrator that the periodic refresh does
  not exist, that loaded Órganos do not update themselves, or that a refused or failed import must
  be triggered again by hand. A grep for *"Ata que exista"* and *"Mentres non exista"* over `ui/src`
  finds nothing.
- The run banner's success note, the failed note and the abandoned note each state that the schedule
  is what returns to these Órganos; the mark dialog's guard note states that a refused mark costs
  freshness rather than data. (SPEC-0005 #33, #5)
- The `Importado` badge tooltip no longer describes the history as frozen at the moment of import.
- The existing component tests read these strings by key rather than by literal, so they pass
  unchanged; any test asserting a literal is updated with it.
- `npm run lint`, `npm run test` and `npm run build` (which type-checks) pass in `ui/`.
