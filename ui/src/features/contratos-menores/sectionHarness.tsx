import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, Outlet } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { theme } from '../../app/theme';
import type { OrganoOutletContext } from '../../shared/entities/organo';
import { strings } from '../../shared/lib/strings';
import { ContratosMenoresSection } from './ContratosMenoresSection';
import type { ContratosMenoresSummary } from './summary';

export const copy = strings.contratosMenores;

export const ORGANO_ID = 'o-1';
/** Supplied because the context carries it, and asserted absent: the name is the page's. */
export const ORGANO_NAME = 'Servizo Galego de Saúde';
export const SECTION_PATH = `/organo/${ORGANO_ID}/contratos-menores`;

export const YEARS = [2025, 2024, 2023];

export function summary(overrides: Partial<ContratosMenoresSummary> = {}): ContratosMenoresSummary {
  return { years: YEARS, partial: false, updating: true, ...overrides };
}

/**
 * The section as its page mounts it: a layout route that cedes an outlet
 * carrying the context, with the section as its only child. The summary is
 * handed over as data, which is the whole arrangement under test — nothing here
 * imports the page, and the page imports nothing here.
 *
 * There is deliberately no `QueryClientProvider`: a section that tried to read
 * anything would throw rather than quietly issue a second request for what it
 * was already given.
 */
export function renderSection(
  sectionSummary: ContratosMenoresSummary,
  initialPath: string = SECTION_PATH,
) {
  const family = { route: 'contratos-menores', summary: sectionSummary };
  const context: OrganoOutletContext = {
    organo: { id: ORGANO_ID, name: ORGANO_NAME, families: { contratosMenores: family } },
    family,
  };
  const router = createMemoryRouter(
    [
      {
        path: '/organo/:id/contratos-menores',
        element: <Outlet context={context} />,
        children: [{ index: true, Component: ContratosMenoresSection }],
      },
    ],
    { initialEntries: [initialPath] },
  );
  const utils = render(
    <MantineProvider theme={theme} env="test">
      <RouterProvider router={router} />
    </MantineProvider>,
  );
  return { ...utils, router };
}

export function yearChooser() {
  return screen.getByRole('combobox', { name: copy.yearLabel });
}

/** What the closed field shows, which is the year the section opened on. */
export function chosenYearShown() {
  return yearChooser().getAttribute('value');
}
