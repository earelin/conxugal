---
feat: FEAT-0016
domain: frontend
adrs: [0003, 0004, 0015]
status: todo
depends_on: [TASK-0007]
---

# The licitacións slice, its route, and its place in the family tabs

The frontend slice this family's section lives in, mounted as a **child route** of
[FEAT-0013](../FEAT-0013-organo-contracts-page/README.md)'s `/organo/:id` page — the arrangement
[FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md) predicted "lets the licitacións
feature add its own tab without touching either".

This task builds the seam and an empty section. The chooser, the row, the filters and the paging are
tasks 10 to 13.

## Scope

- **`ui/src/features/licitacions/`** — a slice of its own, sibling to `features/contratos-menores`,
  named for the family it renders. `eslint-plugin-boundaries` forbids it importing that slice or
  FEAT-0013's page, and forbids either importing it; `app/router.tsx` composes them. Its `index.ts`
  exports **only** the section component.
- **One child route**, `/organo/:id/licitacions`, declared in `app/router.tsx` with the same lazy
  `section()` helper the contratos menores child uses.
- **One `FAMILIES` entry** in `ui/src/features/organo/families.ts` — key `licitacions`, path
  `licitacions`, and the accented Galician label. That file's own comment already says *"A new family
  adds an entry here and a child route in `app/router.tsx`"*, so this is the whole of it.

  ❗ **Registry order decides the redirect.** `FAMILIES` is a list rather than a map so that *the
  first family* means something deterministic — it is what the bare `/organo/:id` redirects to.
  Contratos menores stays first, so an Órgano holding both opens where it opens today and no existing
  acceptance test changes.
- **The `strings` namespaces** — a top-level `licitacions` namespace for this section's copy, and
  `organo.families.licitacions` for the tab label. Copy is Galician and lives in
  `shared/lib/strings.ts`, never inline.
- **The outlet-context narrowing.** The section reads its summary from `useOutletContext()`, narrowing
  the opaque `families.licitacions` entry to the schema
  [TASK-0007](TASK-0007-the-licitacions-read-endpoints.md) declares. `shared/entities/organo.ts`
  types the entry as `unknown` on purpose and **stays that way**: only the owning slice may narrow it,
  and `shared/` deliberately does not know the shape.
- **The read hooks and types** for the two endpoints, keyed on the selection so a held page can never
  survive a change of year, filter or ordering — the `placeholderData` key-prefix comparison
  `features/contratos-menores/contracts.ts` already uses.

**It reads the Órgano nowhere.** The name is rendered by the page above it and the summary arrives as
context, so this slice's own requests are for licitacións alone.

**Out of scope:** the year chooser and the section's statements
([TASK-0010](TASK-0010-year-chooser-and-section-state.md)), the row
([TASK-0011](TASK-0011-the-licitacion-row.md)), the filters
([TASK-0012](TASK-0012-cpv-and-state-filters.md)), sorting and paging
([TASK-0013](TASK-0013-sorting-and-paging-over-the-selection.md)), and **any change to
`features/contratos-menores` or to FEAT-0013's page** beyond the one registry entry.

## Acceptance criteria

- Opening an Órgano that holds **both** families presents its contracts split by family with
  *licitacións* alongside *contratos menores*, and the bare `/organo/:id` still redirects to
  contratos menores.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #27)
- An Órgano with visible contracts of **only one** family shows **only** that family's tab, and the
  absent one causes no error — asserted in both directions, including an Órgano holding licitacións
  and no contratos menores, which is the case that has never existed before. (SPEC-0008 #26, #27)
- An Órgano the server reports **no** `licitacions` entry for draws no licitacións tab and never
  mounts this section. (SPEC-0008 #37)
- Navigating to `/organo/:id/licitacions` mounts the section; navigating away and back remounts it
  with the selection the URL carries. (SPEC-0008 #32)
- An **unauthenticated** visitor navigating to the route is sent to login. (SPEC-0008 #2, #45)
- `eslint-plugin-boundaries` fails the build if this slice imports `features/contratos-menores` or
  `features/organo`, or if either imports this one — asserted by the lint run, not by convention.
- Existing `organo-page` and `contratos-menores` component and acceptance tests pass **unchanged**.
