---
feat: FEAT-0013
domain: frontend
adrs: [0003, 0015, 0018]
status: todo
depends_on: [TASK-0002]
---

# Mount the contratos menores section in the page's outlet

The child route at `/organo/:id/contratos-menores`, declared in `app/router.tsx`, wiring
[FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md)'s section into this page's outlet.

**This is the whole composition, and it is three lines of routing.** That is the point of the
shape: `app/` may import from every feature, so it wires shell and section together while
**neither imports the other**
([ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)). A
second family later adds a registry entry and a child route beside this one and edits neither
slice.

**Also depends on FEAT-0011's section tasks** —
[TASK-0009](../FEAT-0011-contratos-menores-browsing/TASK-0009-year-chooser-and-section-state.md)
onwards, whichever ship the component the barrel exports. That dependency is outside this feature
and so is not in `depends_on:`, which names only tasks here.

## Scope

- **`ui/src/app/router.tsx`** — a child route under the `/organo/:id` layout route at the path the
  family registry names, rendering FEAT-0011's slice barrel, code-split against it exactly as the
  existing sections are and suspending against the same boundary.
- **Nothing else changes.** No component gains an import, no slice gains a dependency, and neither
  `features/organo` nor `features/contratos-menores` is edited: the page already renders an
  `<Outlet/>` with its context, and the section already reads `useOutletContext()`.
- **Tests**: a route-level test mounting the tree in a memory router at
  `/organo/:id/contratos-menores` and asserting the section renders **inside** the page — the name
  and the tab bar present, the active tab matching the URL, and the section's year chooser opening
  on the year the member read's summary carries, with no second request for it.
- **An acceptance test**
  ([ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)) in
  `ui/acceptance` for the journey this task completes and no earlier task can: picking an Órgano in
  the side panel, landing on the page, and seeing that family's contracts — driven against the
  WireMock stub, whose `/api/organo/{id}` mapping [TASK-0002](TASK-0002-organo-page-and-family-tabs.md)
  added and whose contract list mapping is FEAT-0011's.

## Acceptance criteria

- Opening `/organo/:id` for an Órgano holding contratos menores redirects to
  `/organo/:id/contratos-menores` and renders the Órgano's name, the tab bar with that tab active,
  and FEAT-0011's section in the outlet.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #22 contratos-menores half)
- Deep-linking straight to `/organo/:id/contratos-menores` renders the same page, and FEAT-0011's
  `?year=`, `?sort=` and `?page=` ride in the query string beside it. (SPEC-0005 #22, #27)
- The section's year chooser opens on the summary's first year **without issuing a request for the
  summary**: it arrives as outlet context.
- `ui/src/features/organo/` and `ui/src/features/contratos-menores/` import nothing from each
  other, and only `app/router.tsx` imports either; `npm run lint` proves it through
  `eslint-plugin-boundaries`.
- The section ships in its own chunk, not in the page's or the eager one.
- `npm run lint`, `npm run build`, `npm test` and the acceptance suite pass from `ui/`.
