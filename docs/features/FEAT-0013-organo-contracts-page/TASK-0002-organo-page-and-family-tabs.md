---
feat: FEAT-0013
domain: frontend
adrs: [0003, 0004, 0015, 0018]
status: todo
depends_on: [TASK-0001]
---

# The Órgano page: its name, its family tabs, and the outlet it cedes

The `/organo/:id` layout route comes into existence here — the Órgano's name, the tab bar built
from the member read's `families` keys, the redirect from the bare path, and the `<Outlet/>` each
family's section will mount in, **carrying that family's summary as context**.

**It mounts no section, and the outlet stays empty**: the child route that fills it is
[TASK-0003](TASK-0003-mount-contratos-menores-section.md). Until then the page is a frame, which is
exactly what the [design](design/README.md) draws.

Drawn in [`design/organo-page.svg`](design/organo-page.svg),
[`design/organo-page-tabs.svg`](design/organo-page-tabs.svg) and
[`design/organo-page-states.svg`](design/organo-page-states.svg).

## Scope

- **`ui/src/shared/entities/organo.ts`** — the member read, beside the plural `organos.ts`
  [FEAT-0012](../FEAT-0012-organos-visible-set-and-browse/README.md) promotes there, split the same
  way the endpoints are. It lives in `shared/` because **two slices consume the response**: this
  page reads the name and the family keys, and each family's section reads its own entry out of the
  same object — ADR-0015's second consumer, present on arrival rather than anticipated.
  - **`families` is typed as a record of opaque values** — `Record<string, unknown>` — never as a
    union of the known families. A shared module that knew what a `contratos-menores` summary
    contains would be `shared/` depending on a feature, which
    [ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)
    forbids in exactly that direction. Each family's slice narrows its own entry; nothing else may.
  - A `404` from the read is distinguishable from any other failure, because the page renders them
    as two different things.
- **`ui/src/features/organo/`** — a new slice exposing only an `index.ts` barrel, holding the page,
  the tab bar, the family registry and the states. It imports **no other feature** and no other
  feature imports it; `eslint-plugin-boundaries` is what proves it.
- **The family registry** — a list, one entry per family, carrying `slug`, tab label key and
  child-route path. It lives in this slice because it is what the tab bar renders and what
  TASK-0003's child route is declared from, and it is a **list** so that *first family* means
  something deterministic. Today it holds one entry.
- **The layout page**:
  - The Órgano's name as the page title, at page-title size, with **no subtitle** — the tab bar is
    what says what the page holds, and no field of the read carries a description.
  - A Mantine `Tabs` bar rendering **the registry entries present in `families`**, in registry
    order. A key in `families` that the registry does not know is **ignored**, so a server that
    learns a family before this build does draws no tab it cannot route to. A single tab still
    draws the full bar.
  - `<Outlet context={…}/>` carrying the **active family's summary** together with the Órgano, so
    the section has its years without a second request and without importing this slice. The
    active family is the URL's family segment matched against the registry — not a `handle` on the
    child route, which would put the knowledge in `app/`.
- **The bare-path redirect**: `/organo/:id` redirects to the **first registry family present in
  `families`**, once the read resolves. A family segment that is not in the registry, or not in
  `families`, redirects the same way rather than rendering an empty panel or erroring — a URL
  cannot conjure a tab, because the bar is built from the read.
  - **Until TASK-0003 the redirect targets a route that does not exist yet** and the catch-all
    renders the in-shell not-found page. That is the honest transient state of a frame with no
    interior; the redirect's target is asserted in this task's tests, and TASK-0003 is what makes
    it land.
- **The four states, which must not render alike**
  ([design](design/README.md) `organo-page-states.svg`):
  - **loading** — `strings.loading`, reused;
  - **nothing held** (`families: {}`) — the name, `noContracts` and `noContractsHelp`, and **no tab
    bar at all**, with no retry: it is an answer, not a failure;
  - **failed read** — `ErrorAlert` with `errorTitle`, `errorHelp` and the existing `strings.retry`
    wired to the read's `refetch`;
  - **unknown id** (`404`) — `notFoundTitle` and `notFoundHelp`, with no retry.
- **Routing**: `app/router.tsx` gains the `/organo/:id` layout route, code-split against the
  slice's barrel like the existing sections, with the section-level error handler the admin subtree
  already uses. **No child route is declared here.**
- **Copy** in `ui/src/shared/lib/strings.ts` under this slice's namespace, in Galician
  ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC7), with exactly the keys the
  [design's copy table](design/README.md) lists — `families.contratosMenores`, `noContracts`,
  `noContractsHelp`, `errorTitle`, `errorHelp`, `notFoundTitle`, `notFoundHelp`. `strings.retry`
  and `strings.loading` are reused, not duplicated. The tab label lives here because the registry
  does: slug, label and path travel together.
- **The stubbed API**
  ([ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)):
  `ui/wiremock/mappings/organo.json` serving `/api/organo/{id}` for the ids
  `ui/wiremock/mappings/organos.json` already offers the picker — at least one Órgano with a
  `contratos-menores` summary and one with `families: {}` — so dev, preview and the acceptance
  suite can reach both shapes of the page.
- **Tests**: `organo.test.ts` for the read with `nock`, including the `404` path; component tests
  driving accessible roles and the Galician copy for the tab bar as a function of `families`, the
  redirect target, the ignored unknown key, and each of the four states.

## Acceptance criteria

- An Órgano whose read carries one family renders that family's tab and no other; one carrying two
  renders both, in registry order; a family absent from `families` is **absent from the bar** —
  not disabled and not a tab opening an empty panel.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #22, #49)
- An Órgano with `families: {}` renders its name and a plain statement that nothing is held, with
  **no tab bar**, and offers no retry. (SPEC-0005 #26 page half)
- Opening `/organo/:id` lands on the first registry family present in `families`; opening a family
  segment that has no tab lands there too, rather than rendering an empty panel or an error.
  (SPEC-0005 #22)
- A key in `families` that the registry does not know draws no tab and is never a redirect target.
- Loading, `families: {}`, a failed read and a `404` render as **four different things**, and only
  the failed read offers a retry: an empty result is never rendered as an error, nor an error as an
  empty page.
- The page reads **no contract list** and renders **no contract**: the only request the slice makes
  is `GET /api/organo/{id}`.
- The active family's summary reaches the outlet as context, and the page itself reads **no field
  inside any summary** — which tabs to draw and which to redirect to are answered from the keys
  alone.
- `ui/src/features/organo/` imports nothing from another feature and nothing but `app/router.tsx`
  imports it; `shared/entities/organo.ts` types `families` opaquely and narrows no family.
  `npm run lint` proves both through `eslint-plugin-boundaries`.
- All copy is Galician and lives in `strings.ts` under this slice's namespace; the page is usable
  at a 360 px viewport. (SPEC-0001 AC6, AC7)
- `npm run lint`, `npm run build` and `npm test` pass from `ui/`.
