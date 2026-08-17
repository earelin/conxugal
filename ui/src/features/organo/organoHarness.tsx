import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import nock from 'nock';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { theme } from '../../app/theme';
import { createQueryClient } from '../../shared/lib/queryClient';
import { strings } from '../../shared/lib/strings';
import { BASE_URL } from '../../test/renderApp';
import { FAMILIES, type Family } from './families';
import { FamilySectionSpy } from './familySpyHarness';
import { FamilyTabs } from './FamilyTabs';
import { OrganoPage } from './OrganoPage';

/**
 * The fixtures and the two ways in, shared by the page's and the bar's test
 * files. It holds no assertion: what a state should say belongs beside the case
 * asserting it.
 */
export const copy = strings.organo;

export const ORGANO_ID = 'o-1';
export const ORGANO_NAME = 'Servizo Galego de Saúde';

export const contratosMenores = FAMILIES[0];
/**
 * Stands in for the next family to be built, which is what the bar exists from
 * the first day to make room for — and what makes *first family* a different
 * assertion from *only family*.
 */
export const licitacions: Family = {
  key: 'licitacions',
  path: 'licitacions',
  label: 'Licitacións',
};

export const familyEntry = {
  route: contratosMenores.path,
  summary: { years: [2025, 2024, 2023], partial: false, updating: true },
};
export const HOLDS_CONTRATOS_MENORES = { [contratosMenores.key]: familyEntry };

export function member(families: Record<string, unknown>) {
  return { id: ORGANO_ID, name: ORGANO_NAME, families };
}

export function mockOrgano(status: number, body?: object) {
  return nock(BASE_URL).get(`/api/organo/${ORGANO_ID}`).reply(status, body);
}

export function renderOrganoPage(initialPath = `/organo/${ORGANO_ID}`) {
  const router = createMemoryRouter(
    [
      {
        path: '/organo/:id',
        Component: OrganoPage,
        // A splat, so a section with routes of its own stands in for one too.
        children: [{ path: ':family/*', Component: FamilySectionSpy }],
      },
    ],
    { initialEntries: [initialPath] },
  );
  const utils = render(
    <MantineProvider theme={theme} env="test">
      <QueryClientProvider client={createQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>,
  );
  return { ...utils, router };
}

/**
 * The bar on its own, with no read behind it: it is handed the families it
 * draws, so it needs neither a query client nor an Órgano.
 */
export function renderTabs(held: Family[], active: Family) {
  const router = createMemoryRouter(
    [
      {
        path: '/organo/:id/:family',
        element: (
          <FamilyTabs basePath={`/organo/${ORGANO_ID}`} held={held} active={active}>
            <p>{'A sección da familia activa'}</p>
          </FamilyTabs>
        ),
      },
    ],
    { initialEntries: [`/organo/${ORGANO_ID}/${active.path}`] },
  );
  const utils = render(
    <MantineProvider theme={theme} env="test">
      <RouterProvider router={router} />
    </MantineProvider>,
  );
  return { ...utils, router };
}

export function organoHeading() {
  return screen.queryByRole('heading', { name: ORGANO_NAME });
}

export function retryButton() {
  return screen.queryByRole('button', { name: strings.retry });
}

/** Found by name, which is how a screen reader's rotor reaches it. */
export function bar() {
  return screen.getByRole('tablist', { name: copy.tabsLabel });
}

export function tabs() {
  return within(bar()).getAllByRole('tab');
}
