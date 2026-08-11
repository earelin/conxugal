---
feat: FEAT-0012
domain: frontend
adrs: [0004, 0015, 0018]
status: todo
depends_on: []
---

# The administration area reads `GET /api/admin/organos`

[FEAT-0007](../FEAT-0007-organos-taxonomia-classification/README.md)'s administration section —
its taxonomy tree, its classification worklist and its Órgano table — swaps the shared catalogue
read for the `ADMIN`-gated one, so the management surfaces keep the **whole catalogue** once
[TASK-0001](TASK-0001-narrow-organos-read-to-visible-set.md) narrows `GET /api/organos` to the
visible set.

**It lands with or before that task, and it is safe to land alone**: `GET /api/admin/organos`
([FEAT-0009](../FEAT-0009-contratos-menores-initial-import/README.md)) already ships, is already
`ADMIN`-gated, and today serves exactly the same rows in the same order plus the import mark and
import state — its contract says in as many words that it exists so *"the administration UI swaps
one read for the other rather than issuing both"*. Landing it first changes nothing a user sees;
landing it after TASK-0001 silently empties the section an administrator files from.

No component changes, no copy changes and no new state: this is a one-endpoint swap plus the
tests and stubs that name the old path.

## Scope

- `ui/src/features/organos/organos.ts`: `fetchOrganos` requests **`/api/admin/organos`**. The
  `ORGANOS_QUERY_KEY`/`TAXONOMIA_QUERY_KEY` pair, the join in `useOrganosTaxonomia`, its
  all-or-nothing failure rule and every mutation's invalidation are unchanged.
- **The `Organo` type is not widened.** The admin read carries `importable` and `importState` as
  well; no surface in the section renders either yet, and typing fields nothing reads would be a
  second declaration to keep in step with a contract for no gain. They are ignored, deliberately —
  the feature that renders the mark adds them then.
- **`GET /api/organos` keeps its one caller in the section: none.** After this task nothing under
  `features/organos/` requests the shared read; the picker
  ([TASK-0003](TASK-0003-side-panel-organo-picker-tree.md)) is what calls it next, from
  `shared/entities`.
- **The section's error mapping covers `403` on both reads.** The catalogue read is now
  `ADMIN`-gated like the taxonomy write paths, so a session that is no longer an `ADMIN` can be
  refused on either; `strings.admin.organos.errorForbidden` already exists and the section already
  distinguishes it — what changes is that the catalogue read can now produce it too.
- **Tests** — `OrganosPage.test.tsx`, `TaxonomiaManagement.test.tsx`, `OrganoClassification.test.tsx`
  and `OrganosImport.test.tsx` stub the admin path instead. `nock` fails an unmatched request, so
  a stale `/api/organos` stub is caught by the suite rather than by review; one test asserts
  explicitly that the section requests the admin catalogue and never the shared one.
- **The stubbed API** ([ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)):
  `ui/wiremock/mappings/organos.json` gains a `/api/admin/organos` mapping returning the whole
  catalogue — the same fixtures the current `/api/organos` mapping returns, each with `importable`
  and `importState` — and keeps the `/api/organos` mapping as it is. TASK-0003 is what narrows the
  shared stub to a subset, and keeping both distinct is what makes *the picker shows less than the
  administration area* a thing the acceptance suite can see.
- `acceptance/specs/admin-organos.spec.ts` keeps passing with no change to what it drives: the
  section renders the same catalogue from a different path.

## Acceptance criteria

- The administration Órganos section shows **every** Órgano — active and inactive, classified and
  unclassified, with contracts and without — after `GET /api/organos` is narrowed. Its taxonomy
  tree, its Órgano table and its unclassified worklist are unaffected by the narrowing.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #8, #18, #20
  administration half)
- A term that is legitimately empty — one an administrator has just created — is still shown in the
  management tree, and an Órgano holding no contracts is still listed and still classifiable.
  (SPEC-0004 #14, #18)
- The section issues **no** request to `/api/organos`; its catalogue data comes from
  `/api/admin/organos`. (SPEC-0004 #20)
- Importing the catalogue, creating/renaming/moving/deleting a term and assigning/clearing an
  Órgano's placement each still refresh the section from the admin read. (SPEC-0004 #14, #17)
- A failing catalogue read still shows the section's error with a retry and never a partially
  joined tree; a `403` on either read still renders the "no permission" copy rather than the
  generic failure. (SPEC-0004 #1)
- Galician copy is unchanged and no string is added.
  ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC7)
- `npm run lint`, `npm run build` and `npm test` pass from `ui/`, and
  `npm run test:acceptance -- acceptance/specs/admin-organos.spec.ts` passes against the stub.
