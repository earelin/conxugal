import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import nock from 'nock';
import { createMemoryRouter, Outlet } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { theme } from '../../app/theme';
import type { OrganoOutletContext } from '../../shared/entities/organo';
import { createQueryClient } from '../../shared/lib/queryClient';
import { strings } from '../../shared/lib/strings';
import { BASE_URL } from '../../test/renderApp';
import type { ContratoMenor, ContratosMenoresPage } from './contracts';
import { ContratosMenoresSection } from './ContratosMenoresSection';
import type { ContratosMenoresSummary, PublicationYears } from './summary';

export const copy = strings.contratosMenores;

export const ORGANO_ID = 'o-1';
/** Supplied because the context carries it, and asserted absent: the name is the page's. */
export const ORGANO_NAME = 'Servizo Galego de Saúde';
export const SECTION_PATH = `/organo/${ORGANO_ID}/contratos-menores`;

/** The one endpoint this slice reads, as both interceptors below match it. */
const CONTRACTS_ENDPOINT = `/api/organo/${ORGANO_ID}/contratos-menores`;

export const YEARS: PublicationYears = [2025, 2024, 2023];

export function summary(overrides: Partial<ContratosMenoresSummary> = {}): ContratosMenoresSummary {
  return { years: YEARS, partial: false, updating: true, ...overrides };
}

export function contract(overrides: Partial<ContratoMenor> = {}): ContratoMenor {
  return {
    sourceId: 1234567,
    publicationDate: '2025-03-12',
    obxecto: 'Subministración de material funxible de laboratorio',
    amount: 12480,
    duration: '12 meses',
    awardee: { name: 'CLINILAB GALICIA, S.L.', fiscalId: 'ESB15234567' },
    sourceUrl: 'https://www.contratosdegalicia.gal/licitacion?N=1234567',
    ...overrides,
  };
}

export function page(items: ContratoMenor[]): ContratosMenoresPage {
  return { items, page: 1, size: 50, totalItems: items.length, totalPages: 1 };
}

/**
 * The section's one read, matched on the year it asks for as well as the path:
 * an interceptor that ignored the query would answer a request for any year
 * with the same body, and the case that a change of year is a new query would
 * pass without the year ever reaching the server.
 */
export function mockContracts(year: number, status: number, body?: object) {
  return nock(BASE_URL)
    .get(CONTRACTS_ENDPOINT)
    .query({ year: String(year) })
    .reply(status, body);
}

/**
 * The read answered for whichever year is asked for, for the cases that are
 * about the chooser rather than about the list: they still mount the list, and
 * an unmatched request would fail it, but which year reached the server is not
 * what they are asserting.
 */
export function mockAnyContracts() {
  return nock(BASE_URL)
    .persist()
    .get(CONTRACTS_ENDPOINT)
    .query(true)
    .reply(200, page([contract()]));
}

/**
 * The section as its page mounts it: a layout route that cedes an outlet
 * carrying the context, with the section as its only child. The summary is
 * handed over as data, which is the whole arrangement under test — nothing here
 * imports the page, and the page imports nothing here.
 *
 * A query client per render, so no case inherits another's cached page. The
 * client is the section's own read's, not the summary's: that still arrives as
 * context and is never fetched here.
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
  const queryClient = createQueryClient();
  const utils = render(
    <MantineProvider theme={theme} env="test">
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>,
  );
  return { ...utils, router, queryClient };
}

export function yearChooser() {
  return screen.getByRole('combobox', { name: copy.yearLabel });
}

export function contractsTable() {
  return screen.getByRole('table', { name: copy.tableLabel });
}

export function rowFor(contrato: ContratoMenor): HTMLElement {
  return screen.getByText(String(contrato.sourceId)).closest('tr') as HTMLElement;
}
