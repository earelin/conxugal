import { createBrowserRouter, type RouteObject } from 'react-router';
import { AppLayout } from './layout/AppLayout';
import { AboutPage } from './routes/AboutPage';
import { HomePage } from './routes/HomePage';
import { NotFoundPage } from './routes/NotFoundPage';

/**
 * Route tree. Exported separately from the router so tests can mount it with a
 * memory router (see `App.test.tsx`).
 *
 * A single layout route renders the persistent AppShell (SPEC-001 R1); its
 * children are the sections, and the `*` catch-all renders the in-shell
 * not-found page (SPEC-001 R3).
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    Component: AppLayout,
    children: [
      { index: true, Component: HomePage },
      { path: 'acerca', Component: AboutPage },
      { path: '*', Component: NotFoundPage },
    ],
  },
];

export const router = createBrowserRouter(routes);
