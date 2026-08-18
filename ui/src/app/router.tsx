import { type ComponentType, lazy, Suspense } from 'react';
import { createBrowserRouter, type RouteObject } from 'react-router';

import { LoadingIndicator } from '../shared/ui/LoadingIndicator';
import { AdminRoute } from './AdminRoute';
import { AppLayout } from './layout/AppLayout';
import { AboutPage } from './pages/AboutPage';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';
import { RouteErrorPage } from './pages/RouteErrorPage';

// Each import names a feature's barrel because that is the only entry point
// `eslint-plugin-boundaries` lets the app layer reach, which also decides the
// split: the two administration pages share a barrel, so they share a chunk.
// The module registry dedupes, so warming and rendering pull the same fetch.
const administration = () => import('../features/administration');
const contratosMenores = () => import('../features/contratos-menores');
const organo = () => import('../features/organo');
const organos = () => import('../features/organos');

function warmAdminSections() {
  void administration();
  void organos();
}

function section<M>(load: () => Promise<M>, pick: (module: M) => ComponentType): ComponentType {
  const Lazy = lazy(() => load().then((module) => ({ default: pick(module) })));
  return function Section() {
    return (
      <Suspense fallback={<LoadingIndicator />}>
        <Lazy />
      </Suspense>
    );
  };
}

/**
 * Route tree. Exported separately from the router so tests can mount it with a
 * memory router (see `App.test.tsx`).
 *
 * A single layout route renders the persistent AppShell; its children are the
 * sections, and the `*` catch-all renders the in-shell not-found page. Only the
 * shell and the three pages above ship in the eager chunk; the sections are
 * code-split and suspend against the boundary in `AppLayout`.
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    Component: AppLayout,
    // Catches what the section-level handler below does not: a render error in
    // one of the eager pages, and any future section added outside the admin
    // subtree that forgets its own handler. Replaces the shell, which is the
    // honest outcome when the failure could be the shell itself.
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, Component: HomePage },
      { path: 'acerca', Component: AboutPage },
      {
        path: 'administracion',
        element: <AdminRoute fallback={<NotFoundPage />} warm={warmAdminSections} />,
        // A chunk that 404s after a redeploy rejects rather than resolving — the
        // server deliberately does not serve the shell in place of a missing
        // asset. Handled a level below the layout so the section is replaced and
        // the shell around it survives.
        errorElement: <RouteErrorPage />,
        children: [
          { index: true, Component: section(administration, (m) => m.DashboardPage) },
          { path: 'usuarios', Component: section(administration, (m) => m.UsersPage) },
          { path: 'organos', Component: section(organos, (m) => m.OrganosPage) },
        ],
      },
      {
        // The layout route each contract family's section mounts into. Composing
        // shell and section here is what lets neither slice import the other:
        // the page cedes an outlet, the section reads the context it carries,
        // and this is the only module that names both. A family the system
        // gains later adds a registry entry beside the page's and a sibling
        // below, and edits neither slice.
        path: 'organo/:id',
        Component: section(organo, (m) => m.OrganoPage),
        errorElement: <RouteErrorPage />,
        children: [
          {
            path: 'contratos-menores',
            Component: section(contratosMenores, (m) => m.ContratosMenoresSection),
          },
        ],
      },
      { path: '*', Component: NotFoundPage },
    ],
  },
];

export const router = createBrowserRouter(routes);
