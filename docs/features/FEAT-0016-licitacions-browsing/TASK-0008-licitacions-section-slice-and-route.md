---
feat: FEAT-0016
domain: frontend
adrs: [0003, 0004, 0015, 0018]
status: todo
depends_on: []
---

# The licitacións slice, its route, and its place in the family tabs

The frontend slice this family's section lives in, mounted as a **child route** of
[FEAT-0013](../FEAT-0013-organo-contracts-page/README.md)'s `/organo/:id` page — the arrangement
[FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md) predicted "lets the licitacións
feature add its own tab without touching either".

This task builds the seam and an empty section. The chooser, the row, the filters and the paging are
tasks 9 to 12.

## Scope

- **`ui/src/features/licitacions/`** — a slice of its own, sibling to `features/contratos-menores`,
  named for the family it renders. `eslint-plugin-boundaries` forbids it importing that slice or
  FEAT-0013's page, and forbids either importing it; `app/router.tsx` composes them. Its `index.ts`
  exports **only** the section component.
- **One child route**, `/organo/:id/licitacions`, declared in `app/router.tsx` with the same lazy
  `section()` helper the contratos menores child uses.
- ❗ **`loadOrganoPage()` becomes wrong and is fixed here.** `app/router.tsx:33` unconditionally warms
  the **contratos menores** chunk on every Órgano page, justified by a comment about avoiding four
  sequential hops. Once a second family exists, opening a **licitacións-only** Órgano downloads a
  chunk it can never use *and still* pays the full hop — reintroducing the exact defect that comment
  prevents, for the family this feature exists to serve. The warm becomes conditional on what the
  member read says the Órgano holds, or warms both; either way `app/router.tsx` is already in this
  task's scope.
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

**It is the seam and nothing else**, and that is what lets it carry **no `depends_on` at all** and
land in parallel with the whole backend. An earlier draft also gave it the read hooks for the two
endpoints, which made it wait on [TASK-0007](TASK-0007-the-licitacions-read-endpoints.md) and serialised
every frontend task behind the entire server. The reads land with the controls that consume them —
the list read with [TASK-0010](TASK-0010-the-licitacion-row.md), the filter options with
[TASK-0011](TASK-0011-cpv-and-state-filters.md) — so nothing here fetches anything.

**It reads the Órgano nowhere.** The name is rendered by the page above it and the summary arrives as
context, so this slice's own requests are for licitacións alone.

**Out of scope:** the year chooser and the section's statements
([TASK-0009](TASK-0009-year-chooser-and-section-state.md)), the row
([TASK-0010](TASK-0010-the-licitacion-row.md)), the filters
([TASK-0011](TASK-0011-cpv-and-state-filters.md)), sorting and paging
([TASK-0012](TASK-0012-sorting-and-paging-over-the-selection.md)), and **any change to
`features/contratos-menores` or to FEAT-0013's page** beyond the one registry entry.

## Acceptance criteria

- Opening an Órgano that holds **both** families presents its contracts split by family with
  *licitacións* alongside *contratos menores*, and the bare `/organo/:id` still redirects to
  contratos menores.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #27)
- An Órgano with visible contracts of **only one** family shows **only** that family's tab, and the
  absent one causes no error — asserted in both directions, including an Órgano holding licitacións
  and no contratos menores, which is the case that has never existed before. (SPEC-0008 #27)
- An Órgano the server reports **no** `licitacions` entry for draws no licitacións tab and never
  mounts this section. (SPEC-0008 #37)
- Navigating to `/organo/:id/licitacions` mounts the section; navigating away and back remounts it
  with whatever the URL carries. *(No criterion — mounting is a precondition for #32, which [TASK-0009](TASK-0009-year-chooser-and-section-state.md) claims.)*
- The route declares an **`errorElement`**, so a failed read inside the section does not blank the
  Órgano page above it.
- *(An unauthenticated visitor is denied by the endpoint and by the shell's existing route guard —
  [TASK-0007](TASK-0007-the-licitacions-read-endpoints.md)'s and FEAT-0013's respectively. This slice
  adds no guard of its own and can prove neither, so it claims neither #2 nor #45.)*
- `eslint-plugin-boundaries` fails the build if this slice imports `features/contratos-menores` or
  `features/organo`, or if either imports this one — asserted by the lint run, not by convention.
- ❗ **`licitacions` is currently the codebase's stand-in for *a family this build does not know*, and
  the `FAMILIES` entry makes it a family this build knows.** Two kinds of site are affected and they
  take **opposite** remedies:

  **Three assertions invert**, and are re-pointed at a *different* unknown key so each goes on testing
  what it was written to test:

  - `families.test.ts` — `familiesHeld({ licitacions: summary })` is `[]`;
  - `OrganoPage.test.tsx` — no tab bar for a member holding only that key;
  - `app/organoSection.test.tsx` — `/organo/o-1/licitacions` renders the shell's not-found page.

  **Two fixtures do not invert — they graduate.** `organoHarness.tsx`'s `licitacions` and
  `FamilyTabs.stories.tsx`'s both exist to stand for *"the next family to be built"*, in their own
  words. That family now exists, so they become the real `FAMILIES[1]` rather than a second fake key.
  `FamilyTabs.test.tsx` consumes the harness symbol and follows it.

  ❗ **One `licitacions` site in `OrganoPage.test.tsx` must NOT be re-pointed** — the one asserting the
  redirect target — or the test stops covering the two-family case it was written for.

  This is an expected, bounded edit outside the feature's *nothing in contratos menores is touched*
  rule; the README's seams section names it, and a task author who treats it as a violation will stop
  for no reason.
- Every **contratos menores** component and acceptance test passes unchanged.
