import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
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

const UNMARKED = { importable: false, importState: 'NEVER_STARTED' } as const;

const sergas: Organo = {
  id: 'o-1',
  name: 'Servizo Galego de Saúde',
  active: true,
  termoId: 't-2',
  ...UNMARKED,
};
const vivenda: Organo = {
  id: 'o-3',
  name: 'Instituto Galego da Vivenda e Solo',
  active: true,
  termoId: null,
  ...UNMARKED,
};
/** Arrives only in the catalogue read that follows a successful import. */
const turismo: Organo = {
  id: 'o-4',
  name: 'Axencia de Turismo de Galicia',
  active: true,
  termoId: null,
  ...UNMARKED,
};

const CATALOGUE = [sergas, vivenda];

function mockCatalogue(organos: Organo[]) {
  return nock(BASE_URL).get('/api/admin/organos').reply(200, organos);
}

function mockTaxonomia(termos: Termo[]) {
  return nock(BASE_URL).get('/api/organos/taxonomia').reply(200, termos);
}

function outcome(status: ImportOutcome['status'], counts: Partial<ImportOutcome> = {}) {
  return { status, added: 0, refreshed: 0, deactivated: 0, ...counts };
}

/** The one request this suite is about; every case answers it differently. */
function postImport() {
  return nock(BASE_URL).post(IMPORT_PATH);
}

function mockImport(body: ImportOutcome) {
  return postImport().reply(200, body);
}

/**
 * An import the test finishes on demand. A wall-clock `delay()` would be a race
 * against user-event's own awaits: on a slow enough run the reply lands first
 * and the in-flight state under assertion is never observed.
 */
function heldImport(body: ImportOutcome) {
  let release = () => {};
  const answered = new Promise<void>((resolve) => {
    release = resolve;
  });
  postImport().reply(200, (_uri, _requestBody, respond) => {
    void answered.then(() => {
      respond(null, body);
    });
  });
  // Safe to hand out directly: a Promise executor runs synchronously, so the
  // binding is the resolver rather than the placeholder by the time we return.
  return { release };
}

/**
 * A source failure has to arrive as `application/problem+json` or the client
 * parses it as a bare `HttpError`, the problem `type` is lost, and it degrades
 * to the generic transport message.
 */
function mockSourceFailure() {
  return postImport().reply(
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
async function renderLoadedSection() {
  mockCatalogue(CATALOGUE);
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
        `5 ${copy.added.plural} · 2 ${copy.refreshed.plural} · 0 ${copy.deactivated.plural}`,
      ),
    ).toBeInTheDocument();
    // Polite, not assertive: a confirmation of what was asked for has no
    // business interrupting whatever a screen reader is part-way through.
    expect(screen.getByRole('status')).toHaveTextContent(copy.successTitle);
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
        `1 ${copy.added.singular} · 1 ${copy.refreshed.singular} · 1 ${copy.deactivated.singular}`,
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
    // Asserted only once the button is idle again: the invalidation this
    // outcome must not trigger is issued while the mutation settles, so a
    // check taken any earlier could pass without having ruled anything out.
    await waitFor(() => {
      expect(importButton()).toBeEnabled();
    });
    expect(refetch.isDone()).toBe(false);
    expect(nock.pendingMocks()).toHaveLength(1);
  });

  it('lets the administrator dismiss an outcome once it has been read', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockImport(outcome('ALREADY_RUNNING'));

    await user.click(importButton());
    await screen.findByText(copy.alreadyRunning);

    await user.click(screen.getByRole('button', { name: copy.dismiss }));

    expect(screen.queryByText(copy.alreadyRunning)).not.toBeInTheDocument();
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
        `0 ${copy.added.plural} · 0 ${copy.refreshed.plural} · 0 ${copy.deactivated.plural}`,
      ),
    ).not.toBeInTheDocument();
    // A failed import is not a failed section: what was already read stays up.
    expect(screen.getByText(vivenda.name)).toBeInTheDocument();
    expect(screen.getByRole('tree')).toBeInTheDocument();
  });

  it('offers a retry after a failed request, distinct from the source failure message', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    postImport().reply(500);

    await user.click(importButton());

    expect(await screen.findByText(copy.errorGeneric)).toBeInTheDocument();
    expect(screen.queryByText(copy.errorSource)).not.toBeInTheDocument();

    mockImport(outcome('SUCCESS', { refreshed: 3 }));
    mockCatalogue(CATALOGUE);

    await user.click(screen.getByRole('button', { name: strings.retry }));

    expect(await screen.findByText(copy.successTitle)).toBeInTheDocument();
    expect(screen.queryByText(copy.errorTitle)).not.toBeInTheDocument();
  });

  it('keeps the source-failure message and the alert in place across its own retry', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockSourceFailure();

    await user.click(importButton());
    await screen.findByText(copy.errorSource);

    const held = heldImport(outcome('SUCCESS', { refreshed: 1 }));
    mockCatalogue(CATALOGUE);

    await user.click(screen.getByRole('button', { name: strings.retry }));

    // The alert must survive the button that lives inside it: an attempt in
    // flight is not an attempt that never happened, and the message must not
    // degrade to the generic one while the second request runs.
    expect(screen.getByText(copy.errorSource)).toBeInTheDocument();

    held.release();

    expect(await screen.findByText(copy.successTitle)).toBeInTheDocument();
    expect(screen.queryByText(copy.errorSource)).not.toBeInTheDocument();
  });

  it('says a refused import is refused rather than telling the administrator to retry', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    postImport().reply(403);

    await user.click(importButton());

    expect(await screen.findByText(copy.errorForbidden)).toBeInTheDocument();
    expect(screen.queryByText(copy.errorGeneric)).not.toBeInTheDocument();
  });

  it('reports an outcome it does not recognise instead of rendering nothing at all', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    postImport().reply(200, { status: 'PARTIAL', added: 0, refreshed: 0, deactivated: 0 });

    await user.click(importButton());

    expect(await screen.findByText(copy.errorGeneric)).toBeInTheDocument();
    expect(screen.queryByText(copy.successTitle)).not.toBeInTheDocument();
  });

  it('disables the button while the import is in flight, so a double click runs one import', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    const held = heldImport(outcome('SUCCESS', { added: 1 }));
    mockCatalogue([...CATALOGUE, turismo]);
    const second = postImport().reply(200, outcome('SUCCESS'));

    await user.click(importButton());

    const running = await screen.findByRole('button', { name: copy.running });
    expect(running).toBeDisabled();

    // The obvious double click. Being disabled is the mechanism that swallows
    // it; that no second request left the browser is the property that matters.
    await user.click(running);
    expect(second.isDone()).toBe(false);

    held.release();

    expect(await screen.findByText(copy.successTitle)).toBeInTheDocument();
    expect(second.isDone()).toBe(false);
  });
});
