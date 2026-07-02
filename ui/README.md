# conxugal — UI

Interface web de busca e análise de contratos públicos da Xunta de Galicia.

Single-page application built with **Vite**, **React**, **React Router** (library
mode) and **[Mantine](https://ui.mantine.dev)**. The app builds to static assets
that the Micronaut server serves as a single deployable artifact.

Governing decisions: [ADR-0003](../docs/architecture/0003-react-router-ui-served-by-backend.md)
(React Router served by the backend) and
[ADR-0004](../docs/architecture/0004-ui-stack-vite-mantine.md) (Vite + Mantine).
Design: [FEAT-0001](../docs/features/FEAT-0001-ui-application-scaffolding.md).

## Requirements

- Node.js 20+ (developed against Node 24)
- npm

## Getting started

```bash
npm install
npm run dev        # start the dev server (http://localhost:5173)
```

## Scripts

| Script                 | Description                                    |
| ---------------------- | ---------------------------------------------- |
| `npm run dev`          | Start Vite dev server with HMR.                |
| `npm run build`        | Type-check and build static assets to `dist/`. |
| `npm run preview`      | Preview the production build locally.          |
| `npm run lint`         | Run ESLint.                                    |
| `npm run format`       | Format with Prettier.                          |
| `npm run format:check` | Check formatting without writing.              |
| `npm test`             | Run the Vitest suite once.                     |
| `npm run test:watch`   | Run Vitest in watch mode.                      |

## Structure

```
src/
  main.tsx          # React root + MantineProvider + RouterProvider
  router.tsx        # route tree (createBrowserRouter)
  theme.ts          # Mantine theme
  strings.ts        # Galician interface text (i18n seam)
  nav.ts            # primary navigation config
  layout/
    AppLayout.tsx   # Mantine AppShell (header + navbar + <Outlet/>)
  routes/
    HomePage.tsx
    AboutPage.tsx
    NotFoundPage.tsx
  test/
    setup.ts        # jsdom/Mantine test setup
  App.test.tsx      # shell smoke test
```

## Notes

- Routing uses the History API (not hash). Deep-linking in production requires the
  server to serve `index.html` as the SPA fallback for non-API paths — owned by the
  server module (see ADR-0003). The Vite dev server already does this in development.
