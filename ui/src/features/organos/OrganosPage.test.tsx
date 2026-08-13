import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { theme } from '../../app/theme';
import { createQueryClient } from '../../shared/lib/queryClient';
import { strings } from '../../shared/lib/strings';
import type { Organo, Termo } from './organos';
import { OrganosPage } from './OrganosPage';

const BASE_URL = 'http://localhost:3000';

const consellerias: Termo = { id: 't-1', name: 'Consellerías', parentId: null };
const sanidade: Termo = { id: 't-2', name: 'Consellería de Sanidade', parentId: 't-1' };
const concellos: Termo = { id: 't-3', name: 'Concellos', parentId: null };

const sergas: Organo = {
  id: 'o-1',
  name: 'Servizo Galego de Saúde',
  active: true,
  termoId: 't-2',
};
const cunqueiro: Organo = {
  id: 'o-2',
  name: 'Hospital Álvaro Cunqueiro',
  active: false,
  termoId: 't-2',
};
const vivenda: Organo = {
  id: 'o-3',
  name: 'Instituto Galego da Vivenda e Solo',
  active: true,
  termoId: null,
};

function mockCatalogue(organos: Organo[]) {
  return nock(BASE_URL).get('/api/admin/organos').reply(200, organos);
}

function mockTaxonomia(termos: Termo[]) {
  return nock(BASE_URL).get('/api/organos/taxonomia').reply(200, termos);
}

function renderOrganosPage() {
  return render(
    <MantineProvider theme={theme}>
      <QueryClientProvider client={createQueryClient()}>
        <OrganosPage />
      </QueryClientProvider>
    </MantineProvider>,
  );
}

function rowFor(name: string): HTMLElement {
  return screen.getByText(name).closest('tr') as HTMLElement;
}

describe('OrganosPage', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('opens on the unclassified worklist and switches to a term without a second request', async () => {
    const user = userEvent.setup();
    const catalogue = mockCatalogue([sergas, cunqueiro, vivenda]);
    const taxonomia = mockTaxonomia([consellerias, sanidade, concellos]);
    renderOrganosPage();

    expect(await screen.findByText(vivenda.name)).toBeInTheDocument();
    expect(screen.queryByText(sergas.name)).not.toBeInTheDocument();
    expect(screen.getByText(strings.admin.organos.unclassifiedSubtitle)).toBeInTheDocument();

    await user.click(screen.getByText(sanidade.name));

    expect(await screen.findByText(sergas.name)).toBeInTheDocument();
    expect(screen.getByText(cunqueiro.name)).toBeInTheDocument();
    expect(screen.queryByText(vivenda.name)).not.toBeInTheDocument();

    // The worklist is a slice of the catalogue already fetched, so neither
    // selection issues a request of its own.
    expect(catalogue.isDone()).toBe(true);
    expect(taxonomia.isDone()).toBe(true);
    expect(nock.pendingMocks()).toEqual([]);
  });

  it('selects a term with the keyboard alone, since Mantine wires selection to click only', async () => {
    const user = userEvent.setup();
    mockCatalogue([sergas, vivenda]);
    mockTaxonomia([consellerias, sanidade]);
    renderOrganosPage();

    await screen.findByText(vivenda.name);
    const [firstTerm] = screen.getAllByRole('treeitem');
    firstTerm.focus();

    await user.keyboard('{ArrowDown}{Enter}');

    expect(await screen.findByText(sergas.name)).toBeInTheDocument();
    expect(screen.queryByText(vivenda.name)).not.toBeInTheDocument();
  });

  it('marks whichever of the tree and the worklist is open, so the two never both look unselected', async () => {
    const user = userEvent.setup();
    mockCatalogue([sergas, vivenda]);
    mockTaxonomia([consellerias, sanidade]);
    renderOrganosPage();

    const worklist = await screen.findByRole('button', {
      name: new RegExp(strings.admin.organos.unclassified),
    });
    expect(worklist).toHaveAttribute('aria-current', 'true');

    await user.click(screen.getByText(sanidade.name));

    expect(worklist).not.toHaveAttribute('aria-current', 'true');
    // Scoped to the tree — the term name is also in the breadcrumb and the pane
    // title once open — and to the innermost node, since an ancestor treeitem's
    // accessible name swallows its descendants' labels.
    const treeRow = within(screen.getByRole('tree'))
      .getByText(sanidade.name)
      .closest('[role="treeitem"]');
    expect(treeRow).toHaveAttribute('aria-selected', 'true');
  });

  it('shows each Organo of the selected term with its active state, keeping inactive ones visible', async () => {
    const user = userEvent.setup();
    mockCatalogue([sergas, cunqueiro]);
    mockTaxonomia([consellerias, sanidade]);
    renderOrganosPage();

    await user.click(await screen.findByText(sanidade.name));

    expect(
      within(rowFor(sergas.name)).getByText(strings.admin.organos.stateActive),
    ).toBeInTheDocument();
    expect(
      within(rowFor(cunqueiro.name)).getByText(strings.admin.organos.stateInactive),
    ).toBeInTheDocument();
  });

  it('renders the whole catalogue as unclassified when the taxonomia is empty', async () => {
    mockCatalogue([sergas, vivenda]);
    mockTaxonomia([]);
    renderOrganosPage();

    expect(await screen.findByText(strings.admin.organos.treeEmpty)).toBeInTheDocument();
    expect(screen.getByText(sergas.name)).toBeInTheDocument();
    expect(screen.getByText(vivenda.name)).toBeInTheDocument();
  });

  it('shows an error with a working retry when the taxonomia read fails, not an empty tree', async () => {
    const user = userEvent.setup();
    mockCatalogue([sergas, cunqueiro, vivenda]);
    nock(BASE_URL).get('/api/organos/taxonomia').reply(500);
    renderOrganosPage();

    expect(await screen.findByText(strings.admin.organos.errorTitle)).toBeInTheDocument();
    // The failure must not reach the builder as an empty taxonomy, which would
    // present the whole catalogue as unclassified.
    expect(screen.queryByText(strings.admin.organos.treeEmpty)).not.toBeInTheDocument();
    expect(screen.queryByText(sergas.name)).not.toBeInTheDocument();
    expect(screen.queryByText(vivenda.name)).not.toBeInTheDocument();

    mockCatalogue([sergas, cunqueiro, vivenda]);
    mockTaxonomia([consellerias, sanidade]);

    await user.click(screen.getByRole('button', { name: strings.retry }));

    expect(await screen.findByText(vivenda.name)).toBeInTheDocument();
    expect(screen.queryByText(strings.admin.organos.errorTitle)).not.toBeInTheDocument();
  });

  it('shows an error with a working retry when the catalogue read fails', async () => {
    const user = userEvent.setup();
    nock(BASE_URL).get('/api/admin/organos').reply(500);
    mockTaxonomia([consellerias, sanidade]);
    renderOrganosPage();

    expect(await screen.findByText(strings.admin.organos.errorTitle)).toBeInTheDocument();
    expect(screen.queryByText(sanidade.name)).not.toBeInTheDocument();

    mockCatalogue([vivenda]);
    mockTaxonomia([consellerias, sanidade]);

    await user.click(screen.getByRole('button', { name: strings.retry }));

    expect(await screen.findByText(vivenda.name)).toBeInTheDocument();
  });

  it('explains a forbidden taxonomia read rather than showing the generic failure', async () => {
    mockCatalogue([sergas]);
    nock(BASE_URL).get('/api/organos/taxonomia').reply(403);
    renderOrganosPage();

    expect(await screen.findByText(strings.admin.organos.errorForbidden)).toBeInTheDocument();
  });

  it('explains a forbidden catalogue read too, now that it is the ADMIN-gated one', async () => {
    nock(BASE_URL).get('/api/admin/organos').reply(403);
    mockTaxonomia([consellerias, sanidade]);
    renderOrganosPage();

    expect(await screen.findByText(strings.admin.organos.errorForbidden)).toBeInTheDocument();
  });

  it('reads the admin catalogue and never the shared one, which serves a narrower set', async () => {
    const admin = mockCatalogue([sergas, cunqueiro, vivenda]);
    // Registered so the assertion names the shared read rather than resting on
    // an unmatched-request failure, which any wrong path would produce.
    const shared = nock(BASE_URL).get('/api/organos').reply(200, []);
    mockTaxonomia([consellerias, sanidade, concellos]);
    renderOrganosPage();

    expect(await screen.findByText(vivenda.name)).toBeInTheDocument();

    expect(admin.isDone()).toBe(true);
    expect(shared.isDone()).toBe(false);
  });
});
