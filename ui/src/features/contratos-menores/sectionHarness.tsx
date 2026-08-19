import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
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
import { DEFAULT_SORT } from './selection';
import type { ContratosMenoresSummary, PublicationYears } from './summary';

export const copy = strings.contratosMenores;
/** The control's copy is shared, two other specs taking it unchanged. */
export const paging = strings.pagination;

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

/**
 * One page of the envelope. The totals default to a single page holding
 * everything handed over, which is what a case about the rows rather than about
 * the paging wants; a case about the paging states them.
 */
export function page(
  items: ContratoMenor[],
  overrides: Partial<Omit<ContratosMenoresPage, 'items'>> = {},
): ContratosMenoresPage {
  return { items, page: 1, size: 50, totalItems: items.length, totalPages: 1, ...overrides };
}

/** The whole selection, spelled as the URL and the request both spell it. */
export interface AskedSelection {
  year: number;
  sort?: string;
  page?: number;
}

function askedQuery({ year, sort = DEFAULT_SORT, page: asked = 1 }: AskedSelection) {
  return { year: String(year), sort, page: String(asked) };
}

/**
 * The section's one read, matched on the whole selection it asks for as well as
 * the path: an interceptor that ignored the query would answer a request for any
 * selection with the same body, and the cases that a change of year, of ordering
 * or of page is a new request would pass without any of them reaching the server.
 */
export function mockContracts(
  asked: AskedSelection,
  status: number,
  body?: object,
  /**
   * Held open for this long before answering, for a case about what is on
   * screen *while* a read is in flight: without it the reply can land inside
   * the same macrotask as the click, and the case would be racing nock rather
   * than asserting a state.
   */
  delayMillis = 0,
) {
  return nock(BASE_URL)
    .get(CONTRACTS_ENDPOINT)
    .query(askedQuery(asked))
    .delay(delayMillis)
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

export function sortChooser() {
  return screen.getByRole('combobox', { name: copy.sortLabel });
}

/** The shared control, found by the landmark name every list that takes it has. */
export function pagingControl() {
  return screen.getByRole('navigation', { name: paging.navLabel });
}

export function pagingButton(label: string) {
  return within(pagingControl()).getByRole('button', { name: label });
}

/**
 * The jump box, found inside the control rather than by its accessible name:
 * that name states the page total, which is the control's own naming rule and is
 * `Pagination`'s tests to own rather than every list that takes it.
 */
export function pageJump() {
  return within(pagingControl()).getByRole('textbox');
}

export function contractsTable() {
  return screen.getByRole('table', { name: copy.tableLabel });
}

export function rowFor(contrato: ContratoMenor): HTMLElement {
  return screen.getByText(String(contrato.sourceId)).closest('tr') as HTMLElement;
}
