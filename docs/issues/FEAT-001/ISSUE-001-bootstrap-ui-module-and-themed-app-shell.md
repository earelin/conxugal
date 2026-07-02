---
feat: FEAT-001
adrs: [0003, 0004]
status: done
depends_on: []
---

# Bootstrap ui/ module and themed app shell

Bootstrap the `ui/` module and stand up the themed, navigable shell so the app is
runnable and buildable to static assets.

## Scope
- Vite + React + TypeScript project under `ui/`, npm, tsconfig, ESLint + Prettier, `.gitignore`, baseline npm scripts.
- Wire Mantine: `@mantine/core` + `@mantine/hooks`, `postcss-preset-mantine` + `postcss-simple-vars`, `theme.ts`, `MantineProvider` + `ColorSchemeScript`, global styles.
- React Router (library mode): `router.tsx` with an `AppLayout` route, `HomePage` (index) and a catch-all `NotFoundPage`.
- `AppShell` layout: header with product name, collapsible navbar with primary navigation, Galician interface chrome.
- Vitest + Testing Library smoke test; responsive/a11y polish.

## Acceptance criteria (SPEC-001)
- AC1: root renders the app shell with product name + primary nav.
- AC2/AC3: nav changes section + URL; reload and Back behave.
- AC4: unknown path shows in-shell Galician "page not found".
- AC5: consistent theme on every screen; nav keyboard-operable.
- AC6: no horizontal overflow of primary content at 360px width.
- AC7: nav labels + not-found message in Galician.
- `npm run build` emits static assets to `ui/dist`.

Note: production deep-link fallback (server serving `index.html`) is a dependency on the server module, tracked separately.
