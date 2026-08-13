import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { theme } from '../../../app/theme';
import { createQueryClient } from '../../../shared/lib/queryClient';
import { strings } from '../../../shared/lib/strings';
import type { Organo, Termo } from '../organos';
import { OrganosPage } from '../OrganosPage';
import type { ImportRun, ImportRunState } from './contratosMenores';

const BASE_URL = 'http://localhost:3000';
const IMPORT_PATH = '/api/admin/contratos-menores/import';
const RUN_ID = '0196f3d2-0c4a-7b1e-9f3a-8d2c5e7a1b40';
const RUN_PATH = `/api/admin/import-run/${RUN_ID}`;
const copy = strings.admin.organos.contratosMenores;

const sanidade: Termo = { id: 't-1', name: 'Consellería de Sanidade', parentId: null };

const sergas: Organo = {
  id: 'o-1',
  name: 'Servizo Galego de Saúde',
  active: true,
  termoId: 't-1',
  importable: true,
  importState: 'INCOMPLETE',
};
const innovacion: Organo = {
  id: 'o-2',
  name: 'Axencia Galega de Innovación',
  active: true,
  termoId: null,
  importable: false,
  importState: 'NEVER_STARTED',
};

const CATALOGUE = [sergas, innovacion];

function mockCatalogue() {
  return nock(BASE_URL).get('/api/admin/organos').reply(200, CATALOGUE);
}

function mockTaxonomia() {
  return nock(BASE_URL).get('/api/organos/taxonomia').reply(200, [sanidade]);
}

/** The trigger's own answer: a run identity, before the import has done anything. */
function mockTrigger() {
  return nock(BASE_URL).post(IMPORT_PATH).reply(202, { runId: RUN_ID });
}

/**
 * A refusal has to arrive as `application/problem+json` or the client parses it
 * as a bare `HttpError`, the problem `type` is lost, and two refusals that differ
 * only by type become indistinguishable.
 */
function mockRefusedTrigger(type: string) {
  return nock(BASE_URL)
    .post(IMPORT_PATH)
    .reply(
      409,
      { type: `urn:conxugal:problem-type:${type}`, title: 'Conflict', status: 409 },
      { 'Content-Type': 'application/problem+json' },
    );
}

function run(state: ImportRunState, overrides: Partial<ImportRun> = {}): ImportRun {
  return {
    id: RUN_ID,
    importer: 'CONTRATOS_MENORES',
    state,
    startedAt: '2026-08-13T06:14:02Z',
    finishedAt: '2026-08-13T09:42:00Z',
    added: 18402,
    refreshed: 96,
    coveredOrganos: [
      { organoId: sergas.id, state: 'SUCCEEDED', added: 18402, refreshed: 96, failureReason: null },
      { organoId: innovacion.id, state: 'SUCCEEDED', added: 0, refreshed: 0, failureReason: null },
    ],
    ...overrides,
  };
}

function mockRun(body: ImportRun) {
  return nock(BASE_URL).get(RUN_PATH).reply(200, body);
}

/**
 * A reply the test finishes on demand, rather than racing a wall-clock delay:
 * on a slow enough run `delay()` lands first and the in-flight state under
 * assertion is never observed.
 */
function held(scope: nock.Interceptor, body: unknown) {
  let release = () => {};
  const answered = new Promise<void>((resolve) => {
    release = resolve;
  });
  scope.reply(200, (_uri, _requestBody, respond) => {
    void answered.then(() => {
      respond(null, body);
    });
  });
  // Safe to hand out directly: a Promise executor runs synchronously, so the
  // binding is the resolver rather than the placeholder by the time we return.
  return { release };
}

function heldRun(body: ImportRun) {
  return held(nock(BASE_URL).get(RUN_PATH), body);
}

/** The catalogue import, which holds the same guard the triggers report on. */
function heldCatalogueImport() {
  return held(nock(BASE_URL).post('/api/admin/organos/import'), {
    status: 'SUCCESS',
    added: 0,
    refreshed: 0,
    deactivated: 0,
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

async function renderLoadedSection() {
  mockCatalogue();
  mockTaxonomia();
  renderOrganosPage();
  await screen.findByText(innovacion.name);
}

function triggerButton() {
  return screen.getByRole('button', { name: copy.trigger.button });
}

function catalogueButton() {
  return screen.getByRole('button', { name: strings.admin.organos.import.button });
}

/** Triggers the import and waits for whatever the section decided to show. */
async function trigger(user: UserEvent) {
  await user.click(triggerButton());
}

describe('the contratos menores import', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('reports a run still going, saying when it started and how much is in scope', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    mockRun(run('IN_PROGRESS', { finishedAt: null }));

    await trigger(user);

    expect(await screen.findByText(copy.run.inProgressTitle)).toBeInTheDocument();
    expect(screen.getByText(`2 ${copy.run.scopeCount.plural}`)).toBeInTheDocument();
    // Polite, not assertive: an outcome asked for is not an interruption.
    expect(screen.getByRole('status')).toHaveTextContent(copy.run.inProgressTitle);
  });

  it('reports a finished run in Órganos and contracts', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    mockRun(run('SUCCEEDED'));

    await trigger(user);

    expect(await screen.findByText(copy.run.succeededTitle)).toBeInTheDocument();
    expect(
      screen.getByText(
        `2 ${copy.run.coveredCount.plural} · 18 402 ${copy.run.added.plural} · 96 ${copy.run.refreshed.plural}`,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(copy.run.succeededNote)).toBeInTheDocument();
  });

  it('names the Órgano a partial run failed on, without calling the run a failure', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    mockRun(
      run('PARTIALLY_SUCCEEDED', {
        coveredOrganos: [
          {
            organoId: sergas.id,
            state: 'SUCCEEDED',
            added: 18402,
            refreshed: 96,
            failureReason: null,
          },
          {
            organoId: innovacion.id,
            state: 'FAILED',
            added: 0,
            refreshed: 0,
            failureReason: 'a fonte non respondeu',
          },
        ],
      }),
    );

    await trigger(user);

    expect(await screen.findByText(copy.run.partialTitle)).toBeInTheDocument();
    expect(screen.getByText(copy.run.failedOrganos(1))).toBeInTheDocument();
    expect(screen.getByText(`${innovacion.name} · a fonte non respondeu`)).toBeInTheDocument();
    expect(screen.queryByText(copy.run.failedTitle)).not.toBeInTheDocument();
  });

  it('reports a run in which nothing could be imported as the failure it is', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    mockRun(
      run('FAILED', {
        added: 0,
        refreshed: 0,
        coveredOrganos: [
          {
            organoId: sergas.id,
            state: 'FAILED',
            added: 0,
            refreshed: 0,
            failureReason: 'a fonte non respondeu',
          },
        ],
      }),
    );

    await trigger(user);

    expect(await screen.findByText(copy.run.failedTitle)).toBeInTheDocument();
    // What is already stored survives it, which is the part worth saying.
    expect(screen.getByText(copy.run.failedNote)).toBeInTheDocument();
  });

  it('reports a run whose process died as interrupted, not as still going', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    mockRun(run('ABANDONED', { finishedAt: null }));

    await trigger(user);

    expect(await screen.findByText(copy.run.abandonedTitle)).toBeInTheDocument();
    expect(screen.getByText(copy.run.abandonedNote)).toBeInTheDocument();
    expect(screen.queryByText(copy.run.inProgressTitle)).not.toBeInTheDocument();
  });

  it('re-reads the one run when asked, and says how long ago it last did', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    mockRun(run('IN_PROGRESS', { finishedAt: null }));

    await trigger(user);
    await screen.findByText(copy.run.inProgressTitle);
    expect(screen.getByText(copy.run.checkedJustNow)).toBeInTheDocument();

    const reread = mockRun(run('SUCCEEDED'));
    // The re-read of the catalogue a settled run changes.
    mockCatalogue();

    await user.click(screen.getByRole('button', { name: copy.run.refresh }));

    expect(await screen.findByText(copy.run.succeededTitle)).toBeInTheDocument();
    expect(reread.isDone()).toBe(true);
  });

  it('tells the two refusals apart by problem type, not by their shared status', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockRefusedTrigger('import-already-running');
    await trigger(user);
    expect(await screen.findByText(copy.refusal.guardTrigger)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: copy.run.dismiss }));

    mockRefusedTrigger('organo-not-eligible');
    await trigger(user);

    // Two 409s, differing in nothing but their type, saying different things.
    expect(await screen.findByText(copy.refusal.notEligibleTrigger)).toBeInTheDocument();
    expect(screen.queryByText(copy.refusal.guardTrigger)).not.toBeInTheDocument();
  });

  it('renders a refusal as an outcome, with no counts and nothing styled as a failure', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockRefusedTrigger('import-already-running');

    await trigger(user);

    const banner = await screen.findByRole('status');
    expect(within(banner).getByText(copy.refusal.guardNote)).toBeInTheDocument();
    expect(screen.queryByText(copy.run.failedTitle)).not.toBeInTheDocument();
    expect(within(banner).queryByText(copy.run.coveredCount.plural)).not.toBeInTheDocument();
    expect(within(banner).queryByText(copy.run.added.plural)).not.toBeInTheDocument();
  });

  it('keeps the mark when the import it asked for is refused, and says so', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    // The mark applies whether or not an import starts, so the server answers
    // 200 with the refusal in the body rather than a 409.
    nock(BASE_URL)
      .put(`/api/admin/organo/${innovacion.id}/importable`)
      .reply(200, { runId: null, refusal: 'IMPORT_ALREADY_RUNNING' });
    nock(BASE_URL)
      .get('/api/admin/organos')
      .reply(200, [sergas, { ...innovacion, importable: true }]);

    await user.click(screen.getByRole('switch', { name: `${copy.markLabel}: ${innovacion.name}` }));
    await user.click(await screen.findByRole('button', { name: copy.mark.submit }));

    expect(await screen.findByText(copy.refusal.markKeptTitle)).toBeInTheDocument();
    expect(screen.getByText(copy.refusal.guardMark(innovacion.name))).toBeInTheDocument();
    // The row stays marked: only the import was refused.
    const row = screen.getByText(innovacion.name).closest('tr') as HTMLElement;
    expect(await within(row).findByText(copy.badge.marked)).toBeInTheDocument();
  });

  it('offers another attempt after the guard refusal, which no other refusal does', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockRefusedTrigger('import-already-running');
    await trigger(user);
    await screen.findByText(copy.refusal.guardTrigger);
    expect(screen.getByRole('button', { name: strings.retry })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: copy.run.dismiss }));

    mockRefusedTrigger('organo-not-eligible');
    await trigger(user);

    await screen.findByText(copy.refusal.notEligibleTrigger);
    // Asking again answers the same until the catalogue or the mark moves.
    expect(screen.queryByRole('button', { name: strings.retry })).not.toBeInTheDocument();
  });

  it('holds both triggers, with the guard named, while a run is known to be going', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    mockRun(run('IN_PROGRESS', { finishedAt: null }));

    await trigger(user);
    await screen.findByText(copy.run.inProgressTitle);

    expect(triggerButton()).toBeDisabled();
    expect(catalogueButton()).toBeDisabled();
    // Named *on* both buttons, not merely present somewhere on the page: the
    // in-progress banner carries this sentence too, so an assertion that only
    // looked for the text would pass with the toolbar saying nothing at all.
    const describedBy = triggerButton().getAttribute('aria-describedby');
    expect(document.getElementById(describedBy ?? '')).toHaveTextContent(copy.trigger.guardHeld);
    expect(catalogueButton()).toHaveAttribute('aria-describedby', describedBy);
  });

  it('names the guard while the catalogue import holds it, where no banner says it', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    // The one path with no run banner to supply the sentence. A disabled button
    // takes no focus and fires no pointer events, so a tooltip could never
    // reach a reader here — the reason has to be on screen.
    const held = heldCatalogueImport();

    await user.click(catalogueButton());

    await waitFor(() => {
      expect(triggerButton()).toBeDisabled();
    });
    const guard = screen.getByText(copy.trigger.guardHeld);
    expect(guard).toBeVisible();
    expect(triggerButton()).toHaveAttribute('aria-describedby', guard.id);

    held.release();

    await waitFor(() => {
      expect(triggerButton()).toBeEnabled();
    });
    expect(screen.queryByText(copy.trigger.guardHeld)).not.toBeInTheDocument();
  });

  it('keeps holding both triggers after the in-progress report is closed', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    mockRun(run('IN_PROGRESS', { finishedAt: null }));

    await trigger(user);
    await screen.findByText(copy.run.inProgressTitle);

    await user.click(screen.getByRole('button', { name: copy.run.dismiss }));

    // Closing a report is not forgetting the run behind it: the import is still
    // going, and the guard it holds does not care whether anyone is looking.
    expect(screen.queryByText(copy.run.inProgressTitle)).not.toBeInTheDocument();
    expect(triggerButton()).toBeDisabled();
    expect(catalogueButton()).toBeDisabled();
    expect(screen.getByText(copy.trigger.guardHeld)).toBeVisible();
  });

  it('releases both triggers once the run it was holding them for has settled', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    const held = heldRun(run('SUCCEEDED'));

    await trigger(user);

    // Held from the moment the trigger answered, not from the first read: the
    // guard is taken before anything can be read back.
    await waitFor(() => {
      expect(catalogueButton()).toBeDisabled();
    });

    held.release();

    expect(await screen.findByText(copy.run.succeededTitle)).toBeInTheDocument();
    expect(catalogueButton()).toBeEnabled();
    expect(triggerButton()).toBeEnabled();
  });

  it('reports a request that failed as a failure, distinct from a refusal', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    nock(BASE_URL).post(IMPORT_PATH).reply(500);

    await trigger(user);

    expect(await screen.findByText(copy.trigger.errorGeneric)).toBeInTheDocument();
    expect(screen.queryByText(copy.refusal.guardTrigger)).not.toBeInTheDocument();
    // A failed trigger is not a failed section: what was read stays on screen.
    expect(screen.getByText(innovacion.name)).toBeInTheDocument();
  });

  it('says a refused trigger is refused rather than telling the administrator to retry', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    nock(BASE_URL).post(IMPORT_PATH).reply(403);

    await trigger(user);

    expect(await screen.findByText(copy.trigger.errorForbidden)).toBeInTheDocument();
    expect(screen.queryByText(copy.trigger.errorGeneric)).not.toBeInTheDocument();
  });

  it('keeps a failed trigger reported while its own retry is in flight', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    nock(BASE_URL).post(IMPORT_PATH).reply(500);
    await trigger(user);
    await screen.findByText(copy.trigger.errorGeneric);

    const second = held(nock(BASE_URL).post(IMPORT_PATH), { runId: RUN_ID });

    await user.click(screen.getByRole('button', { name: strings.retry }));

    // The alert must survive the button that lives inside it: an attempt in
    // flight is not an attempt that never happened, and unmounting the alert
    // would take the focused element with it.
    expect(screen.getByText(copy.trigger.errorGeneric)).toBeInTheDocument();

    mockRun(run('SUCCEEDED'));
    second.release();

    expect(await screen.findByText(copy.run.succeededTitle)).toBeInTheDocument();
    expect(screen.queryByText(copy.trigger.errorGeneric)).not.toBeInTheDocument();
  });

  it('says a run that no longer exists is gone rather than blaming the network', async () => {
    const user = userEvent.setup();
    await renderLoadedSection();

    mockTrigger();
    nock(BASE_URL).get(RUN_PATH).reply(
      404,
      {
        type: 'urn:conxugal:problem-type:import-run-not-found',
        title: 'Not Found',
        status: 404,
      },
      { 'Content-Type': 'application/problem+json' },
    );

    await trigger(user);

    expect(await screen.findByText(copy.run.errorNotFound)).toBeInTheDocument();

    // A missing run never comes back, so a report with no way out would sit on
    // screen until the page was reloaded.
    await user.click(screen.getByRole('button', { name: copy.run.dismiss }));

    expect(screen.queryByText(copy.run.errorNotFound)).not.toBeInTheDocument();
  });

  it('reads the elapsed caption in whole hours once minutes stop meaning anything', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      await renderLoadedSection();

      mockTrigger();
      mockRun(run('IN_PROGRESS', { finishedAt: null }));

      await trigger(user);
      await screen.findByText(copy.run.inProgressTitle);
      expect(screen.getByText(copy.run.checkedJustNow)).toBeInTheDocument();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(7 * 60_000);
      });
      expect(
        screen.getByText(`${copy.run.checkedAgoPrefix} 7 ${copy.run.checkedAgoUnit}`),
      ).toBeInTheDocument();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2 * 60 * 60_000);
      });

      // "hai 127 min" is a number to decode rather than a freshness to feel.
      expect(
        screen.getByText(`${copy.run.checkedAgoPrefix} 2 ${copy.run.checkedAgoHourUnit}`),
      ).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});
