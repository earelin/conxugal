---
feat: FEAT-0007
domain: frontend
adrs: [0003, 0004, 0015]
status: todo
depends_on: [TASK-0005]
---

# Órganos section + tree view

The `ADMIN`-only **Órganos** section and everything it takes to *render* the taxonomy:
the route, the slice, the tree builder, and the states around it. **Read-only** — no
mutation controls, which are [TASK-0008](TASK-0008-taxonomy-management-ui.md) to
[TASK-0010](TASK-0010-import-trigger-ui.md). Governed by
[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (Vite + Mantine),
[ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md), and
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)
(feature slices with a shared core).

Visual target: [`design/taxonomy-admin.svg`](design/taxonomy-admin.svg) and
[`design/unclassified-worklist.svg`](design/unclassified-worklist.svg) — the two-pane
layout, tree rows and Órgano table, minus every action button.

## Scope
- A new route and nav entry inside the admin area, gated the same way `AdminRoute` already
  gates the existing pages.
- **The slice lives at `ui/src/features/organos/`**, per ADR-0015: it owns its components,
  its API calls and its local state, exposes one `index.ts` barrel, and imports nothing from
  `routes/admin/`. The existing tree is *not* migrated and `eslint-plugin-boundaries` is
  *not* wired here — see the feature's *UI* section for why, and for what stays unowned.
- Move `ErrorAlert` from `routes/admin/` to `ui/src/shared/ui/`, updating the existing
  importers. This slice needs it, and a feature reaching into another feature's internals is
  the one thing ADR-0015's dependency rule forbids. Give it the **retry** affordance it does
  not have today, which the failed-fetch rule below depends on.
- Fetch both reads — `GET /api/organos` and `GET /api/taxonomy-nodes` — and **build the tree
  in the browser**: a pure function taking the two arrays and returning the rooted tree plus
  the unclassified list, by grouping nodes on `parentId` (null → root) and Órganos on
  `taxonomyNodeId` (null → unclassified). It lives outside the components in its own module
  so it can be unit-tested without rendering, and it is the only place tree shape is
  computed.
- Tolerate a `taxonomyNodeId` that matches no node in the taxonomy response — the two reads
  are separate requests and another admin's delete can land between them. Render such an
  Órgano as unclassified; never drop it and never throw.
- **Distinguish a failed fetch from an empty one.** If either read fails, show `ErrorAlert`
  with a retry instead of rendering the successful half alone — a failed taxonomy fetch must
  never reach the builder as an empty node list, because the tolerance rule above would then
  present the whole catalogue as unclassified and an admin would read a transport failure as
  their taxonomy having been wiped.
- **Sort for display**: nodes among their siblings, and Órganos within a node and in the
  unclassified worklist, by name using `localeCompare` with a Galician locale — the
  endpoints promise no order, and without this the tree reshuffles on the refetch after
  every mutation. Accented names must collate correctly (`Á` beside `A`, not after `Z`).
- The two-pane layout: the `Taxonomía` tree card, and the content card showing the selected
  node's Órganos or the pinned **Sen clasificar** worklist. Each Órgano row shows name,
  active state and nothing else yet.
- Galician chrome and copy, consistent with the existing admin pages.

## Acceptance criteria
- Given the two flat responses, the builder produces the correct rooted tree at several
  levels of nesting, places each Órgano under the node its `taxonomyNodeId` names, and
  returns every null-placement Órgano as unclassified — with an empty taxonomy response, the
  whole catalogue comes back unclassified.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #8, #14, #18)
- An Órgano whose `taxonomyNodeId` matches no node in the taxonomy response is shown as
  unclassified rather than lost or fatal.
- When the taxonomy read fails and the catalogue read succeeds, the section shows an error
  with a working retry — **not** an empty tree with the whole catalogue as unclassified; the
  two states are visibly different. The reverse failure is handled the same way.
- Sibling nodes, and Órganos within a node and in the unclassified worklist, are displayed
  in name order, and accented Galician names sort in their expected places.
- Selecting a node shows its Órganos with name and active state; selecting **Sen
  clasificar** shows the null-placement Órganos — from the catalogue already fetched, with
  no second request. (SPEC-0004 #8, #18)
- The section is not reachable by a `USER`; this gating is cosmetic — `/api/admin/**`
  remains the real gate. (SPEC-0004 #1)
- No file under `features/organos/` imports from `routes/admin/`, `api/` or `commons/`.
- All added chrome and messages are in Galician. ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) #6)
- The tree builder is unit-tested on its own arrays — nesting, unclassified, empty taxonomy,
  dangling node id, sibling ordering — with no component rendered; the page is
  component-tested for selection, the empty-taxonomy state and each failing read, with HTTP
  mocked at the network boundary per the project's `nock`-style convention rather than by
  stubbing the API module.
