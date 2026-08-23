# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the `ui/` module of conxugal — see the root `CLAUDE.md` for the repo-wide
spec-driven workflow (`SPEC → FEAT → TASK`). This module implements
`docs/specs/SPEC-0001-web-ui.md`.

## Commands

Run from `ui/`:

- `npm run dev` — Vite dev server with HMR (<http://localhost:5173>)
- `npm run build` — type-check with TypeScript 7's `tsc -b` then build static assets
  to `dist/`
- `npm run tsc7 -- <args>` — run TypeScript 7's compiler directly (e.g. `npm run tsc7 -- --version`)
- `npm test` — run the Vitest suite once
- `npm test -- src/App.test.tsx` — run a single test file
- `npm test -- -t "shows the Galician not-found"` — run tests matching a name pattern
- `npm run test:watch` — Vitest in watch mode
- `npm run api:up` / `npm run api:down` — start/stop the stubbed API (WireMock)
- `npm run test:acceptance` — Playwright acceptance suite (`npx playwright install chromium` first)
- `npm run test:acceptance -- acceptance/specs/admin-users.spec.ts` — run a single acceptance spec
- `npm run storybook` — the component workshop (<http://localhost:6006>)
- `npm run build-storybook` — build it to `storybook-static/` (what CI runs)
- `npm run lint` — ESLint
- `npm run format` / `npm run format:check` — Prettier write/check

## Before committing

Run `npm run lint`, `npm run build`, `npm run test` and `npm run build-storybook`
from `ui/` and fix any failures before committing changes to this module.

## Architecture

- **Stack**: Vite + React 19 + TypeScript, React Router in *library* mode
  (`createBrowserRouter`, client-side SPA — not framework mode/SSR), Mantine for
  components. Builds to static assets consumed by the Micronaut server as a single
  deployable artifact (ADR-0003, ADR-0004).
- **Module layout** (ADR-0015): `src/` is organized as feature slices with a
  shared core, dependencies pointing only downward —
  `app → features → shared/entities → shared/ui + shared/lib`. `app/` is the
  composition root (router, nav, theme, layout, standalone pages, and the
  `AdminRoute` route guard — it sits here rather than in the administration
  feature because the router imports it statically, and a static import of a
  feature barrel would drag that whole slice into the eager chunk);
  `features/<name>/` owns one buildable feature and exposes only an `index.ts`
  barrel outward — it may group its internals into sub-folders (e.g.
  `administration/{monitoring,users}/`, `organos/{catalogo,taxonomia,imports,dialogs}/`)
  for readability, but these are
  organisational only and not enforced boundaries: `eslint-plugin-boundaries`
  treats everything under `features/<name>/` as one element, and the slice root
  keeps what the sub-folders share — its page, its reads and its view model;
  `shared/entities/`
  holds cross-feature domain types/reads;
  `shared/ui/` and `shared/lib/` hold presentational primitives and
  framework-free utilities respectively. `eslint-plugin-boundaries`
  (`eslint.config.js`) enforces the dependency direction and that a feature can
  only be imported via its barrel.
- **Route tree** (`src/app/router.tsx`): a single layout route (`AppLayout`)
  renders the persistent Mantine `AppShell`; page routes nest as its children
  via `<Outlet/>`. `routes` (the `RouteObject[]`) is exported separately from
  `router` so tests can mount the same tree with `createMemoryRouter` instead of
  a real browser router — see `src/App.test.tsx`. `/organo/:id` is itself a
  layout route: the page draws the Órgano's name and a tab per contract family
  its member read carries, and hands the active family's entry to the section
  mounted in its `<Outlet/>` as context (`OrganoOutletContext` in
  `src/shared/entities/organo.ts`). Composing shell and section in the router is
  what lets neither feature slice import the other.
- **Code splitting** (`src/app/router.tsx`): only the shell and the three
  standalone pages ship in the eager chunk. Feature sections are split with the
  local `section()` helper, which pairs a `React.lazy` component with its own
  `Suspense` boundary. The boundary must live *inside* the route element:
  React Router commits navigations in a transition, so a boundary mounted higher
  up (e.g. around `AppLayout`'s `<Outlet/>`) never shows its fallback and the
  address bar changes while the previous page stays on screen. Each `import()`
  names a feature **barrel**, which is both what `eslint-plugin-boundaries`
  permits and what decides the chunk split — pages sharing a barrel share a
  chunk. `AdminRoute` takes a `warm` callback and fires it on mount: without it
  the guard withholds `<Outlet/>` until `/api/me` answers, queueing the
  section's chunk behind that request on every direct load of an admin URL.
  A section that fails to download (a stale hashed chunk after a redeploy — the
  server 404s those rather than serving the shell) is caught by the
  `errorElement` on its parent route; `RouteErrorPage` inspects the error so it
  only blames a redeployment when that is actually the cause.
- **Session loss** (`src/shared/lib/queryClient.ts`): the server answers an
  unauthenticated XHR with a `401` rather than an HTML redirect (ADR-0005), so a
  gone session surfaces as an `HttpError` and this is where it is acted on. The handler is
  wired into **both** the query and the mutation cache, and redirects to
  `/login` **once** per client — several requests failing together, or a logout
  racing a background refetch, must not each call `location.replace`.
  `redirectToLogin(client)` exports that same guard for callers outside the
  caches (`useLogout`'s own success path). `UserMenu` also inspects a `401` — only
  to stay quiet, leaving the redirect to the shared handler. There is no separate
  session flag to keep in sync: the cookie is the session, `useCurrentUser`'s cache
  is the only session-derived state, and the full-page navigation to `/login`
  discards it.
- **`AdminRoute`** (`src/app/AdminRoute.tsx`) is a **role guard**, not only the
  chunking concern described above — it gates the admin section on the role from
  `/api/me`. Role-gated nav visibility is affordance only; the server is the
  real gate and refuses independently.
- **Entry** (`src/main.tsx`): wraps the tree in `MantineProvider` (theme from
  `src/app/theme.ts`) and `RouterProvider`. History-API routing (not hash) — in
  production the server must serve `index.html` as the SPA fallback for non-API
  paths (owned by the server module, ADR-0003); Vite's dev server does this
  automatically. `<ColorSchemeScript defaultColorScheme="auto">` renders ahead of
  the provider so the stored/system scheme is applied before first paint —
  without it the app flashes the light palette on every load for a reader whose
  scheme resolves to dark.
- **i18n seam** (`src/shared/lib/strings.ts`): all user-facing text (Galician)
  lives in one `strings` object rather than scattered through components, so a
  future i18n feature can lift it into a translation catalogue without
  restructuring call sites. Add new UI copy here, not inline.
- **Navigation** (`src/app/nav.ts`): primary nav items are declared as data
  (`navItems`) and mapped to Mantine `NavLink`s in `AppLayout`, rather than
  hardcoded per-link JSX.
- **Testing**: Vitest + Testing Library + jsdom (configured in `vite.config.ts`,
  setup in `src/test/setup.ts`). Tests render the real route tree via
  `createMemoryRouter` and assert against rendered text from `strings`, not
  hardcoded literals, so assertions stay in sync with `strings.ts` changes.
  Vitest's `include` is scoped to `src/**` so it doesn't collect the Playwright
  specs in `acceptance/`. Note that routes under `/administracion` are
  code-split, so a test rendering one must `findBy*`/`waitFor` — and an
  assertion that such a page is *absent* proves nothing until its module has
  loaded, since it would otherwise pass by outrunning the import (see
  `src/app/AdminRoute.test.tsx`).
- **Component workshop** (`.storybook/`): every reusable component is stored as
  `<Component>.stories.tsx` beside itself, one story per meaningful state, copy read
  from `strings` rather than inlined. `preview.tsx` is a second composition root —
  `MantineProvider` (the real theme) + `QueryClientProvider` + a `createMemoryRouter`
  splat route, which is what lets components calling `useMutation`, `useMatch` or
  rendering router links draw with no server. Four things there are load-bearing and
  non-obvious: it imports `@mantine/charts/styles.css`, which `main.tsx` deliberately
  does *not* (the app defers it into the lazy metrics chunk, and Storybook has no such
  chunk); `reactDocgen` stays at the default JS-based `react-docgen`, because
  `react-docgen-typescript` resolves the `typescript` module and here that name is the
  TS 6 shim; `.storybook/**` is in `boundaries/ignore`, since `no-unknown-files` is an
  error; and `**/*.stories.{ts,tsx}` is **excluded from `tsconfig.app.json`** and checked by
  `tsconfig.storybook.json` instead, which is what keeps Storybook's types — and the
  `@types/node` they pull in — out of a program that runs in the browser. Keep the two
  globs and `main.ts`'s `stories` glob in step: a `.stories.ts` checked by neither
  project is how the split springs a leak. Shared fixtures live in `storyFixtures.ts` files rather than in the
  `*Harness.tsx` modules beside them, which import `vitest` and would drag the test
  runner into the browser bundle. Not stored, on purpose: route-level pages, `AppLayout`
  and `AdminRoute`, and the three containers that hit the network on mount
  (`MetricsPanel`, `ImportToolbar`, `ContratosMenoresList`) — those would need request
  mocking. Note the builder merges `vite.config.ts`, so `npm run storybook` inherits the
  `/api` proxy: a story that writes behaves differently with `npm run api:up` running. Adding a `shared/ui` primitive without a story is a gap; this finds them:

  ```sh
  comm -23 \
    <(find src/features src/shared/ui src/app/layout -name '*.tsx' \
        ! -name '*.test.tsx' ! -name '*Harness.tsx' ! -name '*.stories.tsx' | sort) \
    <(find src -name '*.stories.tsx' | sed 's/\.stories\.tsx/.tsx/' | sort)
  ```

- **Local API / acceptance tests** (ADR-0018): the app calls same-origin `/api`
  paths, and Vite proxies them (dev *and* preview) to a WireMock container in
  `docker-compose.yml`, so the admin area runs with no backend. Stub state lives
  in `wiremock/mappings/`, shared by `npm run dev`, `npm run preview` and the
  black-box Playwright suite in `acceptance/`. `acceptance/` sits outside the `src` module
  graph — it drives the built app over HTTP and imports nothing from `src`
  (hence its own `tsconfig.acceptance.json` and a `boundaries/ignore` entry). Specs
  drive only accessible roles/labels and the Galician copy of `strings.ts`; they
  must not assert on locale-formatted dates, which differ between browser
  builds — a helper that formats numbers has to pin its own separators rather
  than trust the runtime's gl-ES data, as `metricsFormat.ts` does. They run
  serially — WireMock is one shared process.
- **TypeScript 7 / 6 split**: `devDependencies.typescript` is aliased to
  `@typescript/typescript6` (Microsoft's compatibility shim) so anything that
  `require`s/`import`s the `typescript` module — `typescript-eslint`, whose
  `typescript-eslint@8.63.0` peer range is `<6.1.0` — gets the TS 6 compiler API.
  Real TypeScript 7 is installed separately as `devDependencies.typescript7`
  (`npm:typescript@^7`) and invoked directly via the `tsc7` script, since its `tsc`
  binary would otherwise collide with the TS 6 shim's own bundled `tsc`. `npm run
  build` and CI type-checking go through `tsc7`; only the programmatic compiler
  API (e.g. ESLint's type-aware rules) should ever resolve the aliased TS 6
  package.
- **Linting** (`eslint.config.js`): beyond `eslint-plugin-boundaries`, the `**/*.{ts,tsx}`
  block layers `@eslint-react/eslint-plugin` (`recommended-typescript`),
  `eslint-plugin-jsx-a11y` (`recommended`), `eslint-plugin-import-x`
  (`recommended` + `typescript`), `eslint-plugin-sonarjs` (`recommended`),
  `eslint-plugin-no-unsanitized` (`recommended`) and `eslint-plugin-unused-imports`;
  `eslint-plugin-react-you-might-not-need-an-effect` (`recommended`, all warnings)
  rides alongside as its own block. Two seams worth
  knowing: `eslint-plugin-react-hooks` stays the authority on hook usage, so
  eslint-react's duplicate `rules-of-hooks`/`exhaustive-deps` are off; and the
  resolver is configured twice — `import/resolver` for boundaries (which reads the
  eslint-plugin-import key) and `import-x/resolver-next` for import-x. Unused-variable
  reporting moved from `@typescript-eslint/no-unused-vars` to
  `unused-imports/no-unused-vars`, still an error but with a `^_` opt-out prefix;
  unused *imports* are separately an auto-fixable error.
  `eslint-plugin-perfectionist` is registered but only its four import/export
  sorters are on — third-party imports first, then a blank line, then local ones,
  with a source's `import type` kept next to its value import. Its sorters for
  objects, JSX props, interfaces, modules and classes stay off: that ordering
  carries meaning the alphabet does not.
- **Lint blocks for tests and e2e**: test files (`**/*.test.{ts,tsx}` and
  `src/test/**`) additionally get `@vitest/eslint-plugin` and
  `eslint-plugin-testing-library` (`flat/react`). `testing-library/no-container`
  and `no-node-access` are off there — this module asserts on Mantine's responsive
  `visible-from-sm` classes and on `aria-hidden`/`inert` decorative wrappers, and
  an element hidden from the accessibility tree has no Testing Library query to
  reach it by. A separate `e2e/**` block carries `eslint-plugin-playwright`
  (`flat/recommended`); that directory does not exist yet, since ADR-0007 puts
  Playwright in the Java `server/acceptance` module. `tsconfig.node.json` already
  includes `e2e` so the type-aware rules can parse those files when they arrive.
- **Utilities**: use [`es-toolkit`](https://es-toolkit.dev) for common array/object/function
  helpers (`debounce`, `groupBy`, `chunk`, etc.) instead of hand-rolling them. Import from
  `es-toolkit` itself, never from `es-toolkit/compat` — that subpath only exists to match
  lodash's exact (looser) behaviour for projects migrating off it, which doesn't apply here.

<!-- distilled-from: FEAT-0001 @ 3f17cc0 -->
<!-- distilled-from: FEAT-0002 @ 6d8a9f4 -->
