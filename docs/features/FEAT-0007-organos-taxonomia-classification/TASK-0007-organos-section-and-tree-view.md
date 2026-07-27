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
mutation controls, which are [TASK-0008](TASK-0008-taxonomia-management-ui.md) to
[TASK-0010](TASK-0010-import-trigger-ui.md). Governed by
[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (Vite + Mantine),
[ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md), and
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)
(feature slices with a shared core).

Visual target: [`design/taxonomia-admin.svg`](design/taxonomia-admin.svg) and
[`design/unclassified-worklist.svg`](design/unclassified-worklist.svg) — the two-pane
layout, tree rows and Órgano table, minus every action button.

## Scope
- A new route and nav entry inside the admin area, gated the same way `AdminRoute` already
  gates the existing pages.
- **The slice lives at `ui/src/features/organos/`**, per ADR-0015: it owns its components,
  its API calls and its local state, exposes one `index.ts` barrel, and imports nothing from
  `routes/admin/`. The existing tree is *not* migrated and `eslint-plugin-boundaries` is
  *not* wired here — see the feature's *UI* section for why, and for what stays unowned.
- **Promote three files, and no others** (the feature's *UI* section argues each):
  - `ErrorAlert` → `shared/ui/`, gaining the **retry** affordance the failed-fetch rule
    below depends on;
  - `httpClient.ts` (`apiFetch`, `HttpError`) and `httpError.ts` → `shared/lib/`, with
    `api/queryClient.ts` repointed at the new path. This one is load-bearing: the single
    `QueryClient` keys its retry policy *and* its 401→`/login` redirect on
    `error instanceof HttpError`, so a slice with its own error type would retry blindly and
    lose the session-expiry redirect. Exactly one `HttpError` class may exist.

  Update every existing importer. Nothing else moves — the rest of `ui/src` stays as it is.
- Add **`ProblemError`** to `shared/lib/`: `type`, `status` and `detail` parsed from an
  `application/problem+json` body, falling back to today's `HttpError` behaviour when the
  body is not one. `apiFetch` currently discards the body entirely, so without this the
  refusal messages [TASK-0008](TASK-0008-taxonomia-management-ui.md) and
  [TASK-0009](TASK-0009-classification-ui.md) specify — keyed on `type`, never on status —
  cannot be written at all.
- The slice's **HTTP module and refetch hook**: the typed calls for both reads, and the
  single hook that refetches and re-runs the builder. The three later tasks all call it;
  leaving it to whichever landed first would mean it was designed as a side effect of a
  mutation flow.
- **Strings are split, and this task decides the split**: the nav label goes in
  `ui/src/strings.ts`, because `nav.ts` reads `strings.nav.*`; all section copy lives in the
  slice.
- Fetch both reads — `GET /api/organos` and `GET /api/organos/taxonomia` — and **build the tree
  in the browser**: a pure function taking the two arrays and returning the rooted tree plus
  the unclassified list, by grouping terms on `parentId` (null → root) and Órganos on
  `termoId` (null → unclassified). It lives outside the components in its own module
  so it can be unit-tested without rendering, and it is the only place tree shape is
  computed.
- Tolerate a `termoId` that matches no term in the taxonomy response — the two reads
  are separate requests and another admin's delete can land between them. Render such an
  Órgano as unclassified; never drop it and never throw.
- **Distinguish a failed fetch from an empty one.** If either read fails, show `ErrorAlert`
  with a retry instead of rendering the successful half alone — a failed taxonomy fetch must
  never reach the builder as an empty term list, because the tolerance rule above would then
  present the whole catalogue as unclassified and an admin would read a transport failure as
  their taxonomy having been wiped.
- **Sort for display**: terms among their siblings, and Órganos within a term and in the
  unclassified worklist, by name using `localeCompare` with a Galician locale — the
  endpoints promise no order, and without this the tree reshuffles on the refetch after
  every mutation. Accented names must collate correctly (`Á` beside `A`, not after `Z`).
- The two-pane layout: the `Taxonomía` tree card, and the content card showing the selected
  term's Órganos or the pinned **Sen clasificar** worklist. Each Órgano row shows name and
  active state (inactive rows dimmed, never hidden) and nothing else yet.
- **The section chrome is this task's, including the places the later tasks fill**: the tree
  header, the term-content header with its breadcrumb, the per-row slots, and the toolbar —
  all rendered, all with empty action areas. TASK-0008 to TASK-0010 add controls into
  structure that already exists rather than each inventing a container.
- Usable at a **360 px viewport** ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC6): this is
  the repo's first two-pane layout, and two side-by-side cards are exactly what that
  criterion constrains. The panes stack rather than overflow.
- Galician chrome and copy, consistent with the existing admin pages.

## Acceptance criteria
- Given the two flat responses, the builder produces the correct rooted tree at several
  levels of nesting, places each Órgano under the term its `termoId` names, and
  returns every null-placement Órgano as unclassified — with an empty taxonomy response, the
  whole catalogue comes back unclassified.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #8, #14, #18)
- An Órgano whose `termoId` matches no term in the taxonomy response is shown as
  unclassified rather than lost or fatal.
- When the taxonomy read fails and the catalogue read succeeds, the section shows an error
  with a working retry — **not** an empty tree with the whole catalogue as unclassified; the
  two states are visibly different. The reverse failure is handled the same way.
- Sibling terms, and Órganos within a term and in the unclassified worklist, are displayed
  in name order, and accented Galician names sort in their expected places.
- Selecting a term shows its Órganos with name and active state; selecting **Sen
  clasificar** shows the null-placement Órganos — from the catalogue already fetched, with
  no second request. (SPEC-0004 #8, #18)
- The section is not reachable by a `USER`; this gating is cosmetic — `/api/admin/**`
  remains the real gate. (SPEC-0004 #1)
- No file under `features/organos/` imports from `routes/admin/` — the rule ADR-0015 makes
  absolute. It **does** import the promoted files from `shared/`, which is the point of
  promoting them, and exactly one `HttpError` class exists in the app afterwards, still the
  one `api/queryClient.ts` compares against.
- `ProblemError` surfaces `type` from a `problem+json` refusal and degrades to plain
  `HttpError` behaviour on a body that is not one — proven against both shapes, since
  TASK-0008 and TASK-0009 are unimplementable without the first and would crash on the
  second.
- The section is usable at 360 px: both panes reachable, nothing clipped or horizontally
  scrolled. (SPEC-0001 AC6)
- All added chrome and messages are in Galician. (SPEC-0001 AC7)
- The tree builder is unit-tested on its own arrays — nesting, unclassified, empty taxonomy,
  dangling term id, sibling ordering — with no component rendered; the page is
  component-tested for selection, the empty-taxonomy state and each failing read, with HTTP
  mocked at the network boundary per the project's `nock`-style convention rather than by
  stubbing the API module.
