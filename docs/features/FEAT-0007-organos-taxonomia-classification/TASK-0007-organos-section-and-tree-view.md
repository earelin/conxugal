---
feat: FEAT-0007
domain: frontend
adrs: [0003, 0004, 0015]
status: done
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
  another feature.

  > **Superseded on landing.** This task was written expecting to find `ui/src/api/`,
  > `ui/src/commons/` and `ui/src/routes/admin/`, and to promote three files itself. By the
  > time it was picked up the ADR-0015 migration had already landed: `ui/src` is
  > `app/ → features/ → shared/entities → shared/ui + shared/lib` throughout,
  > `eslint-plugin-boundaries` **is** wired in `ui/eslint.config.js`, and `ErrorAlert`,
  > `httpClient.ts` and `httpError.ts` already sit in `shared/`. What survived of the
  > promotion bullet is the **retry affordance** `ErrorAlert` still lacked. The rule the
  > bullet existed to protect is unchanged and still holds: the single `QueryClient` keys
  > its retry policy *and* its 401→`/login` redirect on `error instanceof HttpError`, so
  > exactly one `HttpError` class may exist — which is why `ProblemError` below **extends**
  > it rather than standing beside it.
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
- **Strings, and this task decides where they go**: all of it goes in
  `ui/src/shared/lib/strings.ts` — the nav label under `strings.nav.*`, which `nav.ts`
  reads, and the section copy under `strings.admin.organos.*` beside the two existing admin
  pages.

  > **Superseded on landing.** The split this bullet described assumed a top-level
  > `ui/src/strings.ts` and no shared core. The ADR-0015 migration made
  > `shared/lib/strings.ts` the module's single i18n seam — `ui/CLAUDE.md` requires new copy
  > there and tests assert against `strings.*` rather than literals — so a slice-local
  > catalogue would fork that seam for one section and buy nothing.
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
- **Do not sort.** Both reads promise name order (feature *API surface*), so the builder
  preserves the order it receives and renders it. Grouping by parent keeps siblings in name
  order, because grouping preserves relative order — no per-level sort is needed. A second
  sort in the browser would be a second source of truth, and it would diverge from the
  server's collation on exactly the accented Galician names this catalogue is full of; the
  symptom would look like the server sending wrong data.
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
- Given responses in name order, sibling terms and the Órganos in each term and in the
  worklist are **displayed in that same order**, and re-fetching after a mutation reproduces
  it. Proven by feeding the builder a deliberately unsorted array and asserting the output
  preserves input order rather than repairing it — a builder that quietly sorts would pass
  an "is it in name order?" assertion while hiding the duplicated responsibility.
- Selecting a term shows its Órganos with name and active state; selecting **Sen
  clasificar** shows the null-placement Órganos — from the catalogue already fetched, with
  no second request. (SPEC-0004 #8, #18)
- The section is not reachable by a `USER`; this gating is cosmetic — `/api/admin/**`
  remains the real gate. (SPEC-0004 #1)
- No file under `features/organos/` imports from another feature — the rule ADR-0015 makes
  absolute, and `eslint-plugin-boundaries` now fails the build on it. It **does** import from
  `shared/`, which is the point of the promotions, and exactly one `HttpError` class exists in
  the app afterwards, still the one `shared/lib/queryClient.ts` compares against — with
  `ProblemError` extending it so a `problem+json` refusal stays subject to both policies.
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
