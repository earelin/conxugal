import { createBrowserRouter, type RouteObject } from 'react-router';
import { AppLayout } from './layout/AppLayout';
import { AboutPage } from './routes/AboutPage';
import { AdminRoute } from './routes/admin/AdminRoute';
import { DashboardPage } from './routes/admin/DashboardPage';
import { UsersPage } from './routes/admin/UsersPage';
import { HomePage } from './routes/HomePage';
import { NotFoundPage } from './routes/NotFoundPage';

/**
 * Route tree. Exported separately from the router so tests can mount it with a
 * memory router (see `App.test.tsx`).
 *
 * A single layout route renders the persistent AppShell; its children are the
 * sections, and the `*` catch-all renders the in-shell not-found page.
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    Component: AppLayout,
    children: [
      { index: true, Component: HomePage },
      { path: 'acerca', Component: AboutPage },
      {
        path: 'administracion',
        Component: AdminRoute,
        children: [
          { index: true, Component: DashboardPage },
          { path: 'usuarios', Component: UsersPage },
        ],
      },
      { path: '*', Component: NotFoundPage },
    ],
  },
];

export const router = createBrowserRouter(routes);
