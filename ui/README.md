# conxugal — UI

Interface web de busca e análise de contratos públicos da Xunta de Galicia.

Single-page application built with **Vite**, **React**, **React Router** (library
mode) and **[Mantine](https://ui.mantine.dev)**. The app builds to static assets
that the Micronaut server serves as a single deployable artifact.

Governing decisions: [ADR-0003](../docs/architecture/0003-react-router-ui-served-by-backend.md)
(React Router served by the backend), [ADR-0004](../docs/architecture/0004-ui-stack-vite-mantine.md)
(Vite + Mantine) and [ADR-0015](../docs/architecture/0015-frontend-feature-based-shared-core-modularization.md)
(feature slices with a shared core). Requirements: [SPEC-0001](../docs/specs/SPEC-0001-web-ui.md).

## Requirements

- Node.js — pinned in the repo-root [`.tool-versions`](../.tool-versions) and installed
  with `mise install` ([ADR-0026](../docs/architecture/0026-pinned-toolchain-with-mise.md))
- npm

## Getting started

Dependencies are installed by the repo-root `scripts/setup-dev-env.sh`; `npm ci` from here
does the same thing on its own.

```bash
npm run api:up     # start the stubbed API (WireMock, http://localhost:8081)
npm run dev        # start the dev server (http://localhost:5173)
```

## The local API

The SPA calls same-origin relative paths (`/api/me`, `/api/admin/*`, `POST /logout`)
because in production one server serves both the assets and the API (ADR-0003,
ADR-0006). Locally that origin is a **WireMock** container
([`docker-compose.yml`](docker-compose.yml)) that Vite proxies to, so the whole app —
the `ADMIN`-only administration area included — runs with no backend and no database
([ADR-0018](../docs/architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)).

Its stub state lives in [`wiremock/mappings/`](wiremock/README.md) and is shared by
`npm run dev`, `npm run preview` and the acceptance suite. To develop against a real
backend instead, point the proxy at it:

```bash
UI_API_TARGET=http://localhost:8080 npm run dev
```

## Scripts

| Script                 | Description                                    |
| ---------------------- | ---------------------------------------------- |
| `npm run dev`          | Start Vite dev server with HMR.                |
| `npm run build`        | Type-check and build static assets to `dist/`. |
| `npm run preview`      | Preview the production build locally.          |
| `npm run storybook`    | Start the component workshop (Storybook).      |
| `npm run build-storybook` | Build the workshop to `storybook-static/`.  |
| `npm run lint`         | Run ESLint.                                    |
| `npm run format`       | Format with Prettier.                          |
| `npm run format:check` | Check formatting without writing.              |
| `npm test`             | Run the Vitest suite once.                     |
| `npm run test:watch`   | Run Vitest in watch mode.                      |
| `npm run test:acceptance`     | Run the Playwright acceptance suite.           |
| `npm run test:acceptance:ui`  | Run the acceptance suite in Playwright's UI.   |
| `npm run api:up`       | Start the stubbed API (WireMock).              |
| `npm run api:down`     | Stop it.                                       |

## Component workshop

Every reusable component is stored in [Storybook](https://storybook.js.org), a
state per story, so a component can be built and reviewed without driving the whole
app against WireMock:

```bash
npm run storybook     # http://localhost:6006
```

Stories live beside their component as `<Component>.stories.tsx`. Route-level pages
and the containers that fetch on mount (`MetricsPanel`, `ImportToolbar`,
`ContratosMenoresList`) are deliberately not stored — see `CLAUDE.md`.

`npm run build` type-checks the stories — they are their own TypeScript project,
`tsconfig.storybook.json` — so CI catches a story that stops compiling. It does not
build the workshop, so run `npm run build-storybook` yourself after changing a story
or the Storybook config: bundling and import failures surface only there.

## Acceptance tests

Black-box journeys through the built SPA, driven by
[Playwright](https://playwright.dev) with the API stubbed by the same WireMock
container. They start the app themselves (`vite preview`); you only need the browser
once:

```bash
npx playwright install chromium
npm run test:acceptance
```

Specs live in [`acceptance/specs`](acceptance/specs) and are deliberately limited to high-value
journeys — dashboard status, the user list, creating an account, disabling and
re-enabling one, and admin-nav gating. They run serially: WireMock is a single shared
process, so scenarios that program it would otherwise stomp on each other.

## Structure

Feature slices with a shared core (ADR-0015): dependencies point only downward,
`app → features → shared/entities → shared/ui + shared/lib`.

```
src/
  main.tsx                # React root + MantineProvider + RouterProvider
  App.test.tsx            # shell smoke test
  app/                    # composition root
    router.tsx            # route tree (createBrowserRouter)
    theme.ts              # Mantine theme
    nav.ts                # primary navigation config
    layout/
      AppLayout.tsx       # Mantine AppShell (header + navbar + <Outlet/>)
    pages/
      HomePage.tsx
      AboutPage.tsx
      NotFoundPage.tsx
  features/
    administration/       # one buildable feature per folder, exposes only index.ts outward
      index.ts
      monitoring/
      users/
  shared/
    entities/             # cross-feature domain types/reads
    ui/                   # presentational primitives
    lib/
      strings.ts          # Galician interface text (i18n seam)
  test/
    setup.ts              # jsdom/Mantine test setup
acceptance/               # Playwright acceptance suite (outside the src module graph)
  specs/                  # one file per area of the UI
  support/                # WireMock admin client + fixture values
wiremock/mappings/        # the stubbed API's default state
```

## Notes

- Routing uses the History API (not hash). Deep-linking in production requires the
  server to serve `index.html` as the SPA fallback for non-API paths — owned by the
  server module (see ADR-0003). The Vite dev server already does this in development.
- `main.tsx` renders `<ColorSchemeScript defaultColorScheme="auto">` ahead of the
  provider so the resolved colour scheme applies before first paint, rather than the
  app flashing the light palette on every load.

<!-- distilled-from: FEAT-0001 @ 3f17cc0 -->
