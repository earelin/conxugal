import { expect, type Page, test } from '@playwright/test';

import {
  accounts,
  metricsSamples,
  sampleWithSecrets,
  secretsNeverStreamed,
} from '../support/fixtures';
import { navLink } from '../support/locators';
import {
  clearRequestJournal,
  requestCountFor,
  resetMappings,
  stubEventStream,
} from '../support/wiremock';

const METRICS = '/api/admin/metrics';

test.beforeEach(async () => {
  await resetMappings();
});

test.afterAll(async () => {
  await resetMappings();
});

test.describe('Administration real-time metrics', () => {
  test('streams runtime samples that update on their own, with no manual refresh', async ({
    page,
  }) => {
    // Reconnection is pushed far out so this scenario sees each sample exactly
    // once: what is under test is the panel following the stream, not what it
    // does when the stream ends.
    await stubEventStream(METRICS, metricsSamples, {
      spacingMillis: 1000,
      retryMillis: 30_000,
      heartbeats: 10,
    });

    await page.goto('/administracion');

    await expect(page.getByRole('heading', { name: 'Métricas en tempo real' })).toBeVisible();
    await expect(streamState(page, 'CONECTANDO')).toBeVisible();
    await expect(liveness(page).getByText('Agardando a primeira mostra…')).toBeVisible();

    // The first sample.
    await expect(metricCard(page, 'Fíos activos').getByText('48', { exact: true })).toBeVisible();
    await expect(
      metricCard(page, 'Carga do sistema').getByText('21 %', { exact: true }),
    ).toBeVisible();
    await expect(streamState(page, 'EN DIRECTO')).toBeVisible();

    // The third, reached without a click, a reload or a refresh control — the
    // page is untouched between these two blocks, which is the whole claim.
    await expect(metricCard(page, 'Fíos activos').getByText('47', { exact: true })).toBeVisible();
    await expect(
      metricCard(page, 'Carga do sistema').getByText('18 %', { exact: true }),
    ).toBeVisible();
    await expect(metricCard(page, 'Memoria heap').getByText('35 %', { exact: true })).toBeVisible();
    await expect(
      metricCard(page, 'Peticións HTTP').getByText('18 490', { exact: true }),
    ).toBeVisible();

    // The detail cards render the rest of the same sample. Scoped per card: the
    // HTTP total is shown both as a tile and here, so a page-wide match could be
    // satisfied by the other one.
    const memory = metricCard(page, 'Memoria e recolección de lixo');
    await expect(memory.getByText('355 / 1024 MB · 35 %')).toBeVisible();
    await expect(memory.getByText('128 MB', { exact: true })).toBeVisible();
    await expect(memory.getByText('113', { exact: true })).toBeVisible();
    await expect(memory.getByText('3 d 03 h 52 m', { exact: true })).toBeVisible();

    const pool = metricCard(page, 'Conexións co almacén de datos');
    await expect(pool.getByText('10 / 10 abertas')).toBeVisible();
    await expect(pool.getByText('10 conexións')).toBeVisible();
    await expect(pool.getByText('20 %', { exact: true })).toBeVisible();

    const http = metricCard(page, 'Actividade HTTP');
    await expect(http.getByText('18 490', { exact: true })).toBeVisible();
    await expect(http.getByText('38', { exact: true })).toBeVisible();
    // The error-rate percentage and the GC seconds are the panel's only figures
    // built with Intl, whose separators differ between browser builds — the same
    // reason this suite never asserts on a formatted date. The badge below is
    // the observable proof the rate was computed and classified.
    await expect(http.getByText('NORMAL', { exact: true })).toBeVisible();
  });

  test('keeps a bounded history in the browser and starts empty again after a reload', async ({
    page,
  }) => {
    await stubEventStream(METRICS, metricsSamples, {
      spacingMillis: 800,
      retryMillis: 30_000,
      heartbeats: 10,
    });

    await page.goto('/administracion');

    // The counters are the history made visible: they only reach 3 by having
    // kept the two earlier samples client-side.
    await expect(
      metricCard(page, 'Memoria heap').getByText('355 / 1024 MB · 3/250 mostras'),
    ).toBeVisible();
    await expect(
      metricCard(page, 'Fíos activos').getByText('vivos agora · 3/250 mostras'),
    ).toBeVisible();
    await expect(
      page.getByText(
        'O historial das últimas 250 mostras vive só no navegador: ao recargar a páxina, o panel comeza baleiro.',
      ),
    ).toBeVisible();

    await page.reload();

    // Back to the first sample and a history of one. Anything carried across the
    // reload — a store, storage, a server-side replay — would show 4/250 here.
    await expect(
      metricCard(page, 'Memoria heap').getByText('370 / 1024 MB · 1/250 mostras'),
    ).toBeVisible();
    await expect(metricCard(page, 'Fíos activos').getByText('48', { exact: true })).toBeVisible();
    await expect(metricCard(page, 'Peticións HTTP').getByText('sen historial aínda')).toBeVisible();
  });

  test('holds the last sample while reconnecting and resumes without a reload', async ({
    page,
  }) => {
    // A stub's body always ends, so the drop needs no simulating; the retry
    // window is what makes the reconnecting state sit still long enough to read,
    // and the few heartbeats hold the stream open either side of it.
    await stubEventStream(METRICS, metricsSamples, {
      spacingMillis: 600,
      retryMillis: 2000,
      heartbeats: 3,
    });

    await page.goto('/administracion');
    await expect(streamState(page, 'EN DIRECTO')).toBeVisible();

    await expect(streamState(page, 'RECONECTANDO')).toBeVisible();
    await expect(liveness(page).getByText('Última mostra ás')).toBeVisible();

    // The panel is never emptied or replaced by an error: the last known figures
    // stay on screen, captioned as stale rather than presented as current.
    await expect(metricCard(page, 'Fíos activos').getByText('47', { exact: true })).toBeVisible();
    await expect(
      metricCard(page, 'Memoria heap').getByText('355 / 1024 MB · sen actualizar'),
    ).toBeVisible();
    await expect(
      metricCard(page, 'Carga do sistema').getByText('última carga coñecida'),
    ).toBeVisible();
    await expect(metricCard(page, 'Fíos activos').getByText('último valor recibido')).toBeVisible();
    await expect(metricCard(page, 'Peticións HTTP').getByText('sen novas mostras')).toBeVisible();

    // EventSource reconnects on its own and the history carries on from where it
    // stopped — a reconnect resumes the view, only a reload clears it.
    await expect(streamState(page, 'EN DIRECTO')).toBeVisible();
    await expect(
      metricCard(page, 'Memoria heap').getByText('355 / 1024 MB · 6/250 mostras'),
    ).toBeVisible();
  });

  test('renders only the metrics it knows, never a secret carried alongside them', async ({
    page,
  }) => {
    await stubEventStream(METRICS, [sampleWithSecrets], {
      spacingMillis: 400,
      retryMillis: 30_000,
      heartbeats: 10,
    });

    await page.goto('/administracion');

    const pool = metricCard(page, 'Conexións co almacén de datos');
    await expect(pool.getByText('10 / 10 abertas')).toBeVisible();

    // The pool card is where a connection string would surface if the panel
    // echoed the sample instead of reading named fields from it.
    await expect(pool.getByText('En uso 2')).toBeVisible();
    await expect(pool.getByText('Inactivas 8')).toBeVisible();
    await expect(pool.getByText('Só reconto: sen URL, usuario nin contrasinal.')).toBeVisible();
    await expect(
      page.getByText(
        'As métricas nunca inclúen credenciais, cadeas de conexión nin ningún outro valor secreto.',
      ),
    ).toBeVisible();

    for (const secret of Object.values(secretsNeverStreamed)) {
      await expect(page.getByText(secret, { exact: false })).toHaveCount(0);
    }
  });

  test('stops streaming once the administrator leaves the dashboard', async ({ page }) => {
    // One sample and a short retry make the connect-drop-reconnect cycle a
    // fraction of a second, so a stream left open would rack up requests during
    // the work below rather than sitting idle through it.
    await stubEventStream(METRICS, [metricsSamples[0]], { spacingMillis: 120, retryMillis: 120 });

    await page.goto('/administracion');
    await expect(page.getByRole('heading', { name: 'Métricas en tempo real' })).toBeVisible();
    // A second connection proves the drop-and-retry cycle really is running at
    // that rate, so the count taken afterwards is evidence of something.
    await expect.poll(() => requestCountFor('GET', METRICS)).toBeGreaterThan(1);

    await navLink(page, 'Usuarios').click();
    await expect(page.getByRole('heading', { name: 'Xestión de usuarios' })).toBeVisible();
    await clearRequestJournal();

    // Real work on another page, so the elapsed time is the app's own round
    // trips rather than a sleep — many reconnect cycles' worth either way.
    const row = page.getByRole('row').filter({ hasText: accounts.enabledUser.email });
    for (let round = 0; round < 2; round++) {
      await row.getByRole('button', { name: 'Desactivar', exact: true }).click();
      await expect(row.getByText('Desactivada')).toBeVisible();
      await row.getByRole('button', { name: 'Activar', exact: true }).click();
      await expect(row.getByText('Activada', { exact: true })).toBeVisible();
    }

    expect(await requestCountFor('GET', METRICS)).toBe(0);
  });
});

/** Each metric tile and detail card is a group named after its own label. */
function metricCard(page: Page, name: string) {
  return page.getByRole('group', { name, exact: true });
}

/** The section header's live region — the state badge beside its caption. */
function liveness(page: Page) {
  return page.getByRole('status');
}

// "CONECTANDO" is a substring of "RECONECTANDO", and getByText matches on
// substrings, so the connecting state would otherwise be satisfied by a panel
// that is in fact reconnecting.
function streamState(page: Page, label: 'CONECTANDO' | 'EN DIRECTO' | 'RECONECTANDO') {
  return liveness(page).getByText(label, { exact: true });
}
