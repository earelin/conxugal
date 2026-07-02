# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the `ui/` module of conxugal — see the root `CLAUDE.md` for the repo-wide
spec-driven workflow (`SPEC → FEAT → TASK`). This module implements
`docs/specs/SPEC-001-web-ui.md` via `docs/features/FEAT-001-ui-application-scaffolding.md`;
code comments reference requirement/acceptance-criteria IDs (e.g. `SPEC-001 R1`) back to
that spec.

## Commands

Run from `ui/`:

- `npm run dev` — Vite dev server with HMR (<http://localhost:5173>)
- `npm run build` — type-check (`tsc -b`) then build static assets to `dist/`
- `npm test` — run the Vitest suite once
- `npm test -- src/App.test.tsx` — run a single test file
- `npm test -- -t "shows the Galician not-found"` — run tests matching a name pattern
- `npm run test:watch` — Vitest in watch mode
- `npm run lint` — ESLint
- `npm run format` / `npm run format:check` — Prettier write/check

## Before committing

Run `npm run lint`, `npm run build` and `npm run test` from `ui/` and fix any
failures before committing changes to this module.

## Architecture

- **Stack**: Vite + React 19 + TypeScript, React Router in *library* mode
  (`createBrowserRouter`, client-side SPA — not framework mode/SSR), Mantine for
  components. Builds to static assets consumed by the Micronaut server as a single
  deployable artifact (ADR-0003, ADR-0004).
- **Route tree** (`src/router.tsx`): a single layout route (`AppLayout`) renders the
  persistent Mantine `AppShell`; page routes nest as its children via `<Outlet/>`.
  `routes` (the `RouteObject[]`) is exported separately from `router` so tests can
  mount the same tree with `createMemoryRouter` instead of a real browser router —
  see `src/App.test.tsx`.
- **Entry** (`src/main.tsx`): wraps the tree in `MantineProvider` (theme from
  `src/theme.ts`) and `RouterProvider`. History-API routing (not hash) — in
  production the server must serve `index.html` as the SPA fallback for non-API
  paths (owned by the server module, ADR-0003); Vite's dev server does this
  automatically.
- **i18n seam** (`src/strings.ts`): all user-facing text (Galician) lives in one
  `strings` object rather than scattered through components, so a future i18n
  feature can lift it into a translation catalogue without restructuring call
  sites. Add new UI copy here, not inline.
- **Navigation** (`src/nav.ts`): primary nav items are declared as data
  (`navItems`) and mapped to Mantine `NavLink`s in `AppLayout`, rather than
  hardcoded per-link JSX.
- **Testing**: Vitest + Testing Library + jsdom (configured in `vite.config.ts`,
  setup in `src/test/setup.ts`). Tests render the real route tree via
  `createMemoryRouter` and assert against rendered text from `strings`, not
  hardcoded literals, so assertions stay in sync with `strings.ts` changes.
