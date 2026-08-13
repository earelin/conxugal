import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { theme } from '../../app/theme';
import { createQueryClient } from '../../shared/lib/queryClient';
import { strings } from '../../shared/lib/strings';
import type { ContratosMenoresImportState, Organo, Termo } from './organos';
import { OrganosPage } from './OrganosPage';

const BASE_URL = 'http://localhost:3000';
const RUN_ID = '0196f3d2-0c4a-7b1e-9f3a-8d2c5e7a1b40';
const copy = strings.admin.organos.contratosMenores;

const sanidade: Termo = { id: 't-1', name: 'Consellería de Sanidade', parentId: null };
const TAXONOMIA = [sanidade];

interface Mark {
  importable: boolean;
  importState: ContratosMenoresImportState;
}

function organo(id: string, name: string, mark: Mark, active = true): Organo {
  return { id, name, active, termoId: 't-1', ...mark };
}

/** One Órgano per row state, so the table renders the whole vocabulary at once. */
const nothing = organo('o-1', 'Axencia Galega de Sangue', {
  importable: false,
  importState: 'NEVER_STARTED',
});
const marked = organo('o-2', 'Axencia de Turismo de Galicia', {
  importable: true,
  importState: 'NEVER_STARTED',
});
const partial = organo('o-3', 'Servizo Galego de Saúde', {
  importable: true,
  importState: 'INCOMPLETE',
});
const imported = organo('o-4', 'Fundación Pública Urxencias Sanitarias', {
  importable: true,
  importState: 'COMPLETE',
});
const stale = organo('o-5', 'Concello de Santiago', {
  importable: false,
  importState: 'INCOMPLETE',
});
const inactive = organo(
  'o-6',
  'Hospital Álvaro Cunqueiro',
  { importable: false, importState: 'NEVER_STARTED' },
  false,
);

const CATALOGUE = [nothing, marked, partial, imported, stale, inactive];

function mockCatalogue(organos: Organo[]) {
  return nock(BASE_URL).get('/api/admin/organos').reply(200, organos);
}

function mockTaxonomia() {
  return nock(BASE_URL).get('/api/organos/taxonomia').reply(200, TAXONOMIA);
}

function markPath(organo: Organo): string {
  return `/api/admin/organo/${organo.id}/importable`;
}

/** A mark whose import started, which is the ordinary answer. */
function mockMark(organo: Organo) {
  return nock(BASE_URL).put(markPath(organo)).reply(200, { runId: RUN_ID, refusal: null });
}

function mockRun() {
  return nock(BASE_URL)
    .get(`/api/admin/import-run/${RUN_ID}`)
    .reply(200, {
      id: RUN_ID,
      importer: 'CONTRATOS_MENORES',
      state: 'IN_PROGRESS',
      startedAt: '2026-08-13T06:14:02Z',
      finishedAt: null,
      added: 0,
      refreshed: 0,
      coveredOrganos: [
        { organoId: 'o-2', state: 'PENDING', added: 0, refreshed: 0, failureReason: null },
      ],
    });
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

/** Renders the section on the term holding every row state. */
async function renderTerm(user: UserEvent) {
  mockCatalogue(CATALOGUE);
  mockTaxonomia();
  renderOrganosPage();
  await user.click(await screen.findByText(sanidade.name));
  await screen.findByText(partial.name);
}

function rowFor(organo: Organo): HTMLElement {
  return screen.getByText(organo.name).closest('tr') as HTMLElement;
}

function switchFor(organo: Organo): HTMLElement {
  return screen.getByRole('switch', { name: `${copy.markLabel}: ${organo.name}` });
}

describe('the contratos menores mark', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('renders each row state as its own indicator, so none reads like another', async () => {
    await renderTerm(userEvent.setup());

    expect(within(rowFor(marked)).getByText(copy.badge.marked)).toBeInTheDocument();
    expect(within(rowFor(partial)).getByText(copy.badge.partial)).toBeInTheDocument();
    expect(within(rowFor(imported)).getByText(copy.badge.imported)).toBeInTheDocument();
    expect(within(rowFor(stale)).getByText(copy.badge.stale)).toBeInTheDocument();
    expect(within(rowFor(nothing)).getByText(copy.badge.none)).toBeInTheDocument();
  });

  it('never lets a half-loaded Órgano read as the fully imported one', async () => {
    await renderTerm(userEvent.setup());

    const halfLoaded = within(rowFor(partial)).getByText(copy.badge.partial);
    const complete = within(rowFor(imported)).getByText(copy.badge.imported);

    expect(halfLoaded.textContent).not.toBe(complete.textContent);
    expect(within(rowFor(partial)).queryByText(copy.badge.imported)).not.toBeInTheDocument();
  });

  it('keeps an unmarked Órgano holding contracts visibly apart from one holding none', async () => {
    await renderTerm(userEvent.setup());

    // R5 keeps what was stored, so these two rows must not look alike.
    expect(within(rowFor(stale)).getByText(copy.badge.stale)).toBeInTheDocument();
    expect(within(rowFor(stale)).queryByText(copy.badge.none)).not.toBeInTheDocument();
    expect(within(rowFor(nothing)).queryByText(copy.badge.stale)).not.toBeInTheDocument();
  });

  it('keeps an inactive Órgano listed, with the switch blocked and the reason on screen', async () => {
    await renderTerm(userEvent.setup());

    // Present, not hidden; and the reason is readable without hovering anything.
    expect(rowFor(inactive)).toBeInTheDocument();
    expect(switchFor(inactive)).toBeDisabled();
    expect(within(rowFor(inactive)).getByText(copy.badge.ineligible)).toBeInTheDocument();
  });

  it('counts the marked Órganos in the caption beside the term total', async () => {
    await renderTerm(userEvent.setup());

    expect(
      screen.getByText(
        `6 ${strings.admin.organos.countInTerm.plural} · 3 ${copy.markedTally.plural}`,
      ),
    ).toBeInTheDocument();
  });

  it('asks before marking, naming what the mark costs', async () => {
    const user = userEvent.setup();
    await renderTerm(user);

    await user.click(switchFor(nothing));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(copy.mark.intro(nothing.name))).toBeInTheDocument();
    expect(within(dialog).getByText(copy.mark.costDuration)).toBeInTheDocument();
    expect(within(dialog).getByText(copy.mark.costGuard)).toBeInTheDocument();
  });

  it('writes nothing when the confirmation is cancelled', async () => {
    const user = userEvent.setup();
    await renderTerm(user);
    const write = nock(BASE_URL)
      .put(markPath(nothing))
      .reply(200, { runId: RUN_ID, refusal: null });

    await user.click(switchFor(nothing));
    await user.click(await screen.findByRole('button', { name: copy.mark.cancel }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(write.isDone()).toBe(false);
  });

  it('marks the Órgano through the confirmation and counts it in the caption', async () => {
    const user = userEvent.setup();
    await renderTerm(user);

    mockMark(nothing);
    mockRun();
    mockCatalogue(
      CATALOGUE.map((entry) => (entry.id === nothing.id ? { ...entry, importable: true } : entry)),
    );

    await user.click(switchFor(nothing));
    await user.click(await screen.findByRole('button', { name: copy.mark.submit }));

    expect(await within(rowFor(nothing)).findByText(copy.badge.marked)).toBeInTheDocument();
    expect(
      await screen.findByText(
        `6 ${strings.admin.organos.countInTerm.plural} · 4 ${copy.markedTally.plural}`,
      ),
    ).toBeInTheDocument();
  });

  it('unmarks without a dialog, and the Órgano keeps what it had stored', async () => {
    const user = userEvent.setup();
    await renderTerm(user);

    const unmark = nock(BASE_URL).delete(markPath(partial)).reply(204);
    mockCatalogue(
      CATALOGUE.map((entry) => (entry.id === partial.id ? { ...entry, importable: false } : entry)),
    );

    await user.click(switchFor(partial));

    // No confirmation: nothing is discarded, so there is nothing to weigh up.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(await within(rowFor(partial)).findByText(copy.badge.stale)).toBeInTheDocument();
    expect(unmark.isDone()).toBe(true);
  });

  it('says an Órgano that has left the catalogue cannot be unmarked, and offers the re-read', async () => {
    const user = userEvent.setup();
    await renderTerm(user);

    nock(BASE_URL)
      .delete(markPath(imported))
      .reply(
        404,
        { type: 'urn:conxugal:problem-type:organo-not-found', title: 'Not Found', status: 404 },
        { 'Content-Type': 'application/problem+json' },
      );

    await user.click(switchFor(imported));

    expect(await screen.findByText(copy.write.notFound)).toBeInTheDocument();
  });
});
