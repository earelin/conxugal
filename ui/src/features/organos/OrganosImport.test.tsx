import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { theme } from '../../app/theme';
import { createQueryClient } from '../../shared/lib/queryClient';
import { strings } from '../../shared/lib/strings';
import type { ImportOutcome } from './importOrganos';
import type { Organo, Termo } from './organos';
import { OrganosPage } from './OrganosPage';

const BASE_URL = 'http://localhost:3000';
const IMPORT_PATH = '/api/admin/organos/import';
const copy = strings.admin.organos.import;

const consellerias: Termo = { id: 't-1', name: 'Consellerías', parentId: null };
const sanidade: Termo = { id: 't-2', name: 'Consellería de Sanidade', parentId: 't-1' };

const TAXONOMIA = [consellerias, sanidade];

const sergas: Organo = { id: 'o-1', name: 'Servizo Galego de Saúde', active: true, termoId: 't-2' };
const vivenda: Organo = {
  id: 'o-3',
  name: 'Instituto Galego da Vivenda e Solo',
  active: true,
  termoId: null,
};
/** Arrives only in the catalogue read that follows a successful import. */
const turismo: Organo = {
  id: 'o-4',
  name: 'Axencia de Turismo de Galicia',
  active: true,
  termoId: null,
};

const CATALOGUE = [sergas, vivenda];

function mockCatalogue(organos: Organo[]) {
  return nock(BASE_URL).get('/api/organos').reply(200, organos);
}

function mockTaxonomia(termos: Termo[]) {
  return nock(BASE_URL).get('/api/organos/taxonomia').reply(200, termos);
}

function outcome(status: ImportOutcome['status'], counts: Partial<ImportOutcome> = {}) {
  return { status, added: 0, refreshed: 0, deactivated: 0, ...counts };
}

function mockImport(body: ImportOutcome) {
  return nock(BASE_URL).post(IMPORT_PATH).reply(200, body);
}

/**
 * A source failure has to arrive as `application/problem+json` or the client
 * parses it as a bare `HttpError`, the problem `type` is lost, and it degrades
 * to the generic transport message.
 */
function mockSourceFailure() {
  return nock(BASE_URL).post(IMPORT_PATH).reply(
    500,
    {
      type: 'urn:conxugal:problem-type:organo-import-failed',
      title: 'Import Failed',
      status: 500,
    },
    { 'Content-Type': 'application/problem+json' },
  );
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

/** Renders the section on the worklist, with both reads already answered. */
async function renderLoadedSection(catalogue: Organo[] = CATALOGUE) {
  mockCatalogue(catalogue);
  mockTaxonomia(TAXONOMIA);
  renderOrganosPage();
  await screen.findByText(vivenda.name);
}

function importButton() {
  return screen.getByRole('button', { name: copy.button });
}

describe('Órganos import trigger', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('reports what a successful import changed and brings the new Órganos into the worklist', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();
    expect(screen.queryByText(turismo.name)).not.toBeInTheDocument();

    mockImport(outcome('SUCCESS', { added: 5, refreshed: 2 }));
    // Only the catalogue is refetched, so no second taxonomía scope is stubbed:
    // an unmatched request would fail the read and take the section with it.
    mockCatalogue([...CATALOGUE, turismo]);

    await user.click(importButton());

    expect(await screen.findByText(copy.successTitle)).toBeInTheDocument();
    expect(
      screen.getByText(
        `5 ${copy.addedOther} · 2 ${copy.refreshedOther} · 0 ${copy.deactivatedOther}`,
      ),
    ).toBeInTheDocument();
    // The newly imported Órgano lands in the worklist with no manual reload.
    expect(await screen.findByText(turismo.name)).toBeInTheDocument();
  });

  it('reports a single-Órgano outcome in the singular', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockImport(outcome('SUCCESS', { added: 1, refreshed: 1, deactivated: 1 }));
    mockCatalogue(CATALOGUE);

    await user.click(importButton());

    expect(
      await screen.findByText(
        `1 ${copy.addedOne} · 1 ${copy.refreshedOne} · 1 ${copy.deactivatedOne}`,
      ),
    ).toBeInTheDocument();
  });

  it('shows an import already in progress as information, not as a success or a failure', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockImport(outcome('ALREADY_RUNNING'));
    // Nothing was written, so nothing is re-read: this scope must stay untouched.
    const refetch = mockCatalogue(CATALOGUE);

    await user.click(importButton());

    expect(await screen.findByText(copy.alreadyRunning)).toBeInTheDocument();
    expect(screen.queryByText(copy.successTitle)).not.toBeInTheDocument();
    expect(screen.queryByText(copy.errorTitle)).not.toBeInTheDocument();
    expect(refetch.isDone()).toBe(false);
  });

  it('shows a source failure as a failure, never as a success with three zeroes', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockSourceFailure();

    await user.click(importButton());

    expect(await screen.findByText(copy.errorTitle)).toBeInTheDocument();
    expect(screen.getByText(copy.errorSource)).toBeInTheDocument();
    expect(screen.queryByText(copy.successTitle)).not.toBeInTheDocument();
    // The misreport this screen exists to prevent.
    expect(
      screen.queryByText(
        `0 ${copy.addedOther} · 0 ${copy.refreshedOther} · 0 ${copy.deactivatedOther}`,
      ),
    ).not.toBeInTheDocument();
    // A failed import is not a failed section: what was already read stays up.
    expect(screen.getByText(vivenda.name)).toBeInTheDocument();
    expect(screen.getByRole('tree')).toBeInTheDocument();
  });

  it('offers a retry after a failed request, distinct from the source failure message', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    nock(BASE_URL).post(IMPORT_PATH).reply(500);

    await user.click(importButton());

    expect(await screen.findByText(copy.errorGeneric)).toBeInTheDocument();
    expect(screen.queryByText(copy.errorSource)).not.toBeInTheDocument();

    mockImport(outcome('SUCCESS', { refreshed: 3 }));
    mockCatalogue(CATALOGUE);

    await user.click(screen.getByRole('button', { name: strings.retry }));

    expect(await screen.findByText(copy.successTitle)).toBeInTheDocument();
    expect(screen.queryByText(copy.errorTitle)).not.toBeInTheDocument();
  });

  it('disables the button while the import is in flight, so a double click runs one import', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    nock(BASE_URL)
      .post(IMPORT_PATH)
      .delay(30)
      .reply(200, outcome('SUCCESS', { added: 1 }));
    mockCatalogue([...CATALOGUE, turismo]);
    const second = nock(BASE_URL).post(IMPORT_PATH).reply(200, outcome('SUCCESS'));

    await user.click(importButton());

    const running = await screen.findByRole('button', { name: copy.running });
    expect(running).toBeDisabled();

    await user.click(running);

    expect(await screen.findByText(copy.successTitle)).toBeInTheDocument();
    expect(second.isDone()).toBe(false);
  });
});
