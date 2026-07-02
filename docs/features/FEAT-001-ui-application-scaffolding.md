---
spec: SPEC-001
adrs: [0003, 0004]
status: draft
---

# FEAT-001. UI application scaffolding

## Goal
Stand up the `ui/` module: a buildable, runnable React Router SPA with the Mantine
theme and an application shell, satisfying the *reachable, navigable, themed shell*
requirements of **SPEC-001** and the stack decided in **ADR-0003** (React Router served
by the backend) and **ADR-0004** (Vite build, library-mode SPA, Mantine, npm).

This is the foundation; data browsing/search/export features build on top of it later.

## Scope
- A new `ui/` module: Vite + React + TypeScript, npm.
- Mantine wired through a single `MantineProvider` + a project `theme`.
- React Router in library mode (`createBrowserRouter` + `RouterProvider`).
- An `AppShell`-based layout (header + navbar) as the persistent shell.
- A landing/home section, plus a catch-all not-found route — both inside the shell.
- Galician interface chrome.
- Lint + format + a smoke test, and an npm `build` that emits static assets.

**Out of scope (separate features):** real contract listing/search/detail/export
screens, API client wiring, auth, the backend change that serves `ui/dist` and the SPA
history fallback (owned by the server module).

## Module layout

```
ui/
  package.json
  tsconfig.json
  tsconfig.node.json
  vite.config.ts
  postcss.config.cjs
  index.html
  src/
    main.tsx          # ReactDOM root + RouterProvider
    router.tsx        # createBrowserRouter route tree
    theme.ts          # createTheme — colors, fonts, defaults
    layout/
      AppLayout.tsx   # Mantine AppShell: header + navbar + <Outlet/>
    routes/
      HomePage.tsx
      NotFoundPage.tsx
    components/        # shared presentational components (grows later)
  src/test/setup.ts
```

## Design

### Composition
```mermaid
flowchart TD
    main["main.tsx<br/>RouterProvider"] --> router["router.tsx<br/>createBrowserRouter"]
    router --> layout["AppLayout (AppShell)"]
    layout --> outlet["Outlet"]
    outlet --> home["HomePage"]
    outlet --> nf["NotFoundPage (catch-all)"]
    main --> provider["MantineProvider + theme"]
    provider --> layout
```

### Routing (library mode, ADR-0004)
- `createBrowserRouter` with a single layout route (`path: "/"`, element `<AppLayout/>`).
  - `index: true` → `HomePage` (R1).
  - `path: "*"` → `NotFoundPage` (R3 / AC4), rendered inside the shell.
- History routing (not hash). Deep-linking depends on the backend serving `index.html`
  as the SPA fallback for non-API paths — **flagged for the server feature**, not built
  here. Dev uses Vite's dev server, which already does SPA fallback.

### Theme (R4)
- `theme.ts` exports `createTheme({...})`: primary color, default radius, font family.
  Wrapped once in `main.tsx` via `<MantineProvider theme={theme}>` with
  `<ColorSchemeScript>` for color-scheme support. `import '@mantine/core/styles.css'`.

### Layout / shell (R1, R2, R5)
- `AppLayout` uses Mantine `AppShell` with a `header` (product name "conxugal") and a
  `navbar` carrying primary navigation built from a route list, plus a burger toggle so
  the navbar collapses on narrow viewports (AC6). Active link reflects the current path.
- Content area renders `<Outlet/>`.

### Internationalisation (R6)
- Interface chrome strings (nav labels, not-found copy) are authored in **Galician**
  directly for the scaffold. A full i18n library is out of scope; strings are kept in a
  single module so a later i18n feature can lift them without restructuring.

## Tooling & scripts
- **PostCSS:** `postcss-preset-mantine` + `postcss-simple-vars` (Mantine breakpoints).
- **Lint/format:** ESLint (typescript-eslint, react-hooks, react-refresh) + Prettier.
- **Test:** Vitest + Testing Library + jsdom; one smoke test asserting the shell renders
  the product name and that the not-found route shows the Galician message.
- **npm scripts:** `dev`, `build` (`tsc -b && vite build` → `ui/dist`), `preview`,
  `lint`, `format`, `test`.

## Sequencing (tasks, one small change each)
1. **Bootstrap `ui/`** — Vite React-TS project, npm, tsconfig, ESLint/Prettier, `.gitignore`, baseline scripts. *(ADR-0004)*
2. **Wire Mantine** — install core/hooks, PostCSS preset, `theme.ts`, `MantineProvider` + `ColorSchemeScript`, global styles. *(ADR-0004)*
3. **Router + AppShell layout** — `router.tsx`, `AppLayout`, `HomePage`, `NotFoundPage`, Galician chrome. *(SPEC-001 R1–R3, R6)*
4. **Test + responsive polish** — Vitest setup + smoke test, burger/responsive navbar, a11y pass. *(SPEC-001 R4–R6)*

## Edge cases
- **Unknown deep link in production** → blank/404 unless backend serves `index.html`
  fallback. Mitigation: documented as a dependency on the server feature (R3 fully holds
  only once that lands); in dev it already works.
- **Color-scheme flash** → mitigated by `ColorSchemeScript`.
- **Asset base path** when served under a sub-path by Micronaut → Vite `base` may need
  configuring; deferred until the serving feature fixes the path.
