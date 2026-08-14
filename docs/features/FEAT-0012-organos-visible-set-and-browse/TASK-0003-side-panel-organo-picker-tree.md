---
feat: FEAT-0012
domain: frontend
adrs: [0003, 0004, 0015, 0018]
status: done
depends_on: [TASK-0001, TASK-0002]
---

# The side-panel Órgano picker: the tree

The feature's one `USER` surface, in the state it opens in: a dropdown at the top of the
`AppShell` navbar, present on every route for any authenticated user, holding the **read-only
browse tree** over the narrowed catalogue and
[FEAT-0007](../FEAT-0007-organos-taxonomia-classification/README.md)'s taxonomy read. Choosing an
Órgano opens `/organo/{id}`.

**No route is added and no filter is built yet** — [TASK-0004](TASK-0004-picker-name-filter.md)
adds the text box. The picker is chrome, not a page, which is what removes the
`strings.nav.organos` label collision and what makes *there is no `USER`-facing catalogue list* a
rule with no page to break it
([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) R2).

**It also carries the three promotions to `shared/`**
([ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)), each
triggered by that ADR's own rule — *a thing moves to `shared/` once a second consumer needs it* —
and none of them optional: the navbar must not import `features/organos`, or the admin slice's 39
files land in every route's eager chunk.

It is drawn in [`design/picker-tree.svg`](design/picker-tree.svg) and
[`design/picker-states.svg`](design/picker-states.svg).

**One dependency crosses the feature boundary and is not in `depends_on`, which only names tasks
here**: selection navigates to `/organo/{id}`,
[FEAT-0013](../FEAT-0013-organo-contracts-page/README.md)'s layout route. The picker can land
before that page exists — its criteria below assert the **URL** a selection navigates to, not what
renders there.

## Scope

- **`shared/lib/taxonomiaTree.ts`** — `features/organos/taxonomiaTree.ts` moved verbatim with its
  unit test: `buildTaxonomiaView`, `findTermoPath`, `termoPathLabel`, `PATH_SEPARATOR`, `TermoNode`,
  `TaxonomiaView`, and the `Organo` and `Termo` types with them.
  - **The types travel to `shared/lib`, not `shared/entities`**, because
    `eslint-plugin-boundaries` lets `shared-lib` import only `shared-lib`: the builder cannot reach
    up to the entity module for the types it consumes. The reads stay in `shared/entities`, which
    may import `shared/lib`.
  - **The builder is not modified.** It keeps returning every term, including empty ones — the
    administration tree renders terms an administrator has just created, and a builder that dropped
    them would delete a new term from the management tree the moment it was made.
- **`pruneEmptyTermos(roots)` in the same module** — a **new, separate** pure function, called by
  the picker and by nothing else: a term is kept when its own Órgano list is non-empty **or any
  descendant survives the same test**, recursively. A single-level check would delete exactly the
  intermediate terms a deep taxonomy is made of.
- **`shared/entities/organos.ts`** — the picker's read: `GET /api/organos` (narrowed by
  [TASK-0001](TASK-0001-narrow-organos-read-to-visible-set.md)) and `GET /api/organos/taxonomia`,
  joined through the shared builder, with the same **all-or-nothing** rule
  `useOrganosTaxonomia` already applies: a failing read nulls the view rather than letting the join
  run on half the data, so a failed taxonomy fetch can never render the visible set as one flat
  unclassified heap.
  - The taxonomy read **moves here** and `features/organos` imports it, rather than a second
    fetcher and a second key existing for one endpoint: both surfaces then share one in-flight
    request and one cache entry, and the section's existing `['organos']`-prefixed invalidation
    refreshes the picker when an administrator edits the taxonomy. The narrowed catalogue keeps a
    key of its own — it is a different endpoint from the admin section's.
- **`shared/ui/OrganoPicker.tsx`** — the control:
  - **Closed:** a bordered, full-width trigger under an uppercase dimmed `ÓRGANO` label, naming the
    open Órgano at `fw={600}` or showing the dimmed placeholder when none is open.
  - **Open:** a Mantine `Popover` whose body is the tree — terms with a chevron at `fw={500}`,
    their Órganos one indent deeper, **unclassified Órganos at the root** after the root terms and
    without a chevron, all in the name order the two reads arrive in. Nothing here sorts.
    - **The indent carries Mantine's `withLines` guide lines, and the tree scrolls within
      `60vh`** — neither is in the mockup, and both were added after the tree first shipped
      *flat*: a row's own horizontal padding had been overriding the indent, which is a
      stylesheet rule keyed on the level. At 320 px the names wrap often enough that a 16 px
      step alone reads ambiguously once it is restored, and every branch opens at once, so a
      large visible set would otherwise draw a dropdown taller than the window.
  - **No control that creates, renames, moves, deletes or reassigns anything**, and **no count
    badges**: it is a view, not the admin tree with its buttons hidden (SPEC-0004 #9), and the
    per-term counts the admin tree carries are recorded in the design as deliberately absent.
  - **The open Órgano is shown selected** — Mantine's `light` active state with a check — read from
    the route with `useMatch('/organo/:id/*')` so it holds on every tab of that page.
  - **Selecting navigates to `/organo/{id}`**, closes the popover, and closes the mobile navbar.
  - **Three non-tree states, and the last two must not look alike**: pending renders
    `strings.loading`; an empty visible set renders `empty` + `emptyHelp` with **no retry**; a
    failed read renders `ErrorAlert` with `errorTitle` and the existing `strings.retry`, wired to
    the read's `refetch`. A fresh deployment meets the empty state first, which is why it is a
    correct answer rather than an error.
- **`ui/src/app/layout/AppLayout.tsx`** — the picker renders at the top of `AppShell.Navbar`, above
  `visibleNavSections`, separated by a hairline, for any resolved authenticated user; while the
  session is unresolved it stays hidden, the rule the role-gated nav sections already follow. It is
  **not** a nav item and `nav.ts` is untouched.
- **`ui/src/shared/lib/strings.ts`** — a `organoPicker` namespace with `label`, `placeholder`,
  `empty`, `emptyHelp` and `errorTitle`, in Galician, exactly as the design's copy table records
  them. `strings.retry` and `strings.loading` are reused, not duplicated.
- **The stubbed API**
  ([ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)):
  `ui/wiremock/mappings/organos.json`'s `/api/organos` mapping is narrowed to a **subset** of the
  admin catalogue — including one unclassified Órgano and one inactive one — so dev, preview and
  the acceptance suite see a picker that genuinely shows less than the administration area.
- **Tests:** the moved `taxonomiaTree.test.ts` plus prune cases (a term empty at every level is
  dropped; a term whose own Órganos are absent but whose descendant has one is kept; an empty
  taxonomy prunes to nothing); `OrganoPicker.test.tsx` with `nock`, driving accessible roles and
  the Galician copy from `strings.ts`; `AppLayout.test.tsx` for presence, placement and the
  unresolved-session case. `npm run lint` is what proves the import direction.

## Acceptance criteria

- An authenticated `USER` on any route can open the picker from the side panel and see the browse
  tree of the visible set; there is **no `/organos` route** and no other `USER` surface listing the
  catalogue. (SPEC-0004 #2 deferred half, #9;
  [SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #19)
- The tree offers **no** control to create, rename, move, delete or reassign anything.
  (SPEC-0004 #9; SPEC-0005 #19)
- An Órgano of the visible set that is in **no term** is shown at the **root** of the tree beside
  the root terms, and an **inactive** one holding visible contracts is shown in its term like any
  other. Both are selectable. (SPEC-0004 #19; SPEC-0005 #20)
- A term whose whole subtree holds no Órgano of the visible set is **omitted**; a term whose own
  Órganos are all absent but which has a descendant that is not **is still shown**. The
  administration area shows both, because the prune is the picker's call and not the builder's.
  (SPEC-0004 #22 browse half, #14)
- Selecting an Órgano navigates to `/organo/{id}` for that Órgano's id and closes the dropdown.
  (SPEC-0005 #19)
- The Órgano currently open is shown as selected while the reader is anywhere under
  `/organo/{id}`.
- Loading, an **empty visible set** and a **failed read** render as three different things: only
  the failure offers a retry, and an empty result is never rendered as an error nor an error as an
  empty tree.
- The picker is absent for an unauthenticated visitor and while the session is unresolved.
  (SPEC-0004 #2)
- `ui/src/app/` and `ui/src/shared/` import nothing from `ui/src/features/`, proven by
  `npm run lint` passing with the boundaries rules unchanged; `features/organos` keeps every
  management control and imports the builder and taxonomy read from `shared/`.
- All copy is Galician and lives in `strings.ts` under the picker's namespace; the control is
  usable at a 360 px viewport. ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC6, AC7)
- `npm run lint`, `npm run build` and `npm test` pass from `ui/`.
