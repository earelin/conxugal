---
feat: FEAT-0015
domain: frontend
adrs: [0004, 0015]
status: todo
depends_on: [TASK-0018]
---

# The mark's copy, now that it means both families

[TASK-0018](TASK-0018-start-marked-organo-import.md) makes marking an Órgano import **both**
contract families. Four strings in `ui/src/shared/lib/strings.ts` say it imports contratos menores,
and one of them is the mark switch's **accessible name**. A requirement contradicted by the
interface that reports it is not met in practice, and an administrator reads this copy instead of
the requirement.

`strings.ts` is where the copy lives by `ui/CLAUDE.md`'s i18n-seam convention — *"all user-facing
text (Galician) lives in one `strings` object rather than scattered through components"* — under
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md), on
[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md)'s stack.

A copy-only edit inside the admin Órganos slice. No component, no route, no API call changes.

## Scope

Four strings under `strings.admin.organos.contratosMenores`:

| Key | Says today | Must say |
| --- | --- | --- |
| `markLabel` | *"Importar contratos menores"* | that the mark imports the Órgano's contracts, both families |
| `scopeNote` | *"Só se importan os contratos menores dos órganos marcados e activos…"* | the same about both families |
| `tooltip.none` | *"Non se importa ningún contrato menor deste órgano."* | nothing of this Órgano is imported |
| `badge.marked` tooltip | *"…a primeira importación aínda non gardou ningún contrato."* | unchanged in meaning, checked for family wording |

Proposed Galician, to be settled against the `frontend-design` skill's copy conventions:

- `markLabel`: *"Importar contratos"*
- `scopeNote`: *"Só se importan os contratos dos órganos marcados e activos. A primeira importación
  dun órgano pode durar días."*
- `tooltip.none`: *"Non se importa ningún contrato deste órgano."*

**`markLabel` is the one with teeth.** It is prefixed with the Órgano's name at the call site and is
the switch's accessible name — the switch repeats down the column, so the name is how a screen
reader says which row it is — and it is asserted on by `ContratosMenoresImport.test.tsx` and
`ContratosMenoresMarking.test.tsx`. Both tests read it by key rather than by literal, so they pass
unchanged; any that does assert a literal moves with it.

**Deliberately unchanged:** every string about the **contratos menores** badge column and its
tooltips. That column reports one family's load state, and it still does — a per-family badge is the
browsing feature's, not a rename. The `contratosMenores` key itself stays too: renaming the copy
namespace touches every call site for no behaviour, and the slice is still the contratos menores
admin surface.

**Out of scope:** the run banner, which is
[TASK-0020](TASK-0020-admin-run-banner-after-coverage-rekey.md)'s, and whose *"contratos
engadidos"* counts are deliberately left family-agnostic there for the same reason.

## Acceptance criteria

- No string in `ui/src/shared/lib/strings.ts` tells an administrator that **marking** an Órgano
  imports its contratos menores alone. A grep for *"contratos menores"* over the mark's own copy —
  `markLabel`, `scopeNote`, `tooltip.none` — finds nothing.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #4)
- The mark switch's accessible name still names the Órgano and still says what the switch does, so
  the two component tests that query it by role and name pass.
- The badge column's own copy is unchanged — it reports one family and still says so.
- `npm run lint`, `npm run test` and `npm run build` (which type-checks) pass in `ui/`.
