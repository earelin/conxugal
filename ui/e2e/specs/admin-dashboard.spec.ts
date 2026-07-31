import { expect, test, type Page } from '@playwright/test';
import { resetMappings, stub } from '../support/wiremock';

/** The two status cards are the only named regions on the page. */
function statusCard(page: Page, name: string) {
  return page.getByRole('region', { name, exact: true });
}

function navLink(page: Page, name: string) {
  return page.getByRole('navigation', { name: 'Navegación principal' }).getByRole('link', { name });
}

test.beforeEach(async () => {
  await resetMappings();
});

test.afterAll(async () => {
  await resetMappings();
});

test.describe('Administration dashboard', () => {
  test('shows the service state and datastore reachability to an administrator', async ({
    page,
  }) => {
    await page.goto('/administracion');

    await expect(page.getByRole('heading', { name: 'Panel do sistema' })).toBeVisible();
    await expect(
      page.getByText('Estado operativo do sistema no momento da consulta.'),
    ).toBeVisible();

    await expect(page.getByText('Servizo operativo')).toBeVisible();
    await expect(page.getByText('Todos os compoñentes responden con normalidade.')).toBeVisible();

    // Each card states its value twice — as a value and as a badge — so scope
    // the assertion to the card instead of counting matches page-wide, which
    // would not notice the two cards swapping values.
    await expect(statusCard(page, 'Servizo').getByText('Operativo', { exact: true })).toHaveCount(
      2,
    );
    await expect(
      statusCard(page, 'Base de datos').getByText('Accesible', { exact: true }),
    ).toHaveCount(2);

    // Coarse runtime info, and the promise that it never carries secrets.
    await expect(page.getByText('Información do sistema')).toBeVisible();
    await expect(page.getByText('0.1.0-SNAPSHOT')).toBeVisible();
    await expect(page.getByText('25.0.1 (Eclipse Adoptium)')).toBeVisible();
    await expect(page.getByText('512 / 1024 MB')).toBeVisible();
    await expect(page.getByText('Linux (amd64)')).toBeVisible();
    await expect(
      page.getByText('A información de estado nunca inclúe credenciais nin cadeas de conexión.'),
    ).toBeVisible();
  });

  test('reflects the datastore going unreachable rather than a cached healthy snapshot', async ({
    page,
  }) => {
    await page.goto('/administracion');
    await expect(page.getByText('Servizo operativo')).toBeVisible();

    await stub({
      request: { method: 'GET', urlPath: '/api/admin/system-status' },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: {
          status: 'DEGRADED',
          datastore: { reachable: false },
          checkedAt: '2026-07-31T12:50:00Z',
          application: { version: '0.1.0-SNAPSHOT', environment: 'local' },
          runtime: {
            javaVersion: '25.0.1',
            javaVendor: 'Eclipse Adoptium',
            uptimeMillis: 273420000,
            memoryUsedBytes: 536870912,
            memoryMaxBytes: 1073741824,
            osName: 'Linux',
            osArch: 'amd64',
          },
        },
      },
    });

    // Leave and come back the way a user would. A full reload would drop the
    // query cache and pass even if the dashboard happily served a stale
    // snapshot, which is the thing worth proving here.
    await navLink(page, 'Usuarios').click();
    await expect(page.getByRole('heading', { name: 'Xestión de usuarios' })).toBeVisible();
    await navLink(page, 'Panel').click();

    await expect(page.getByText('Sistema en modo dexenerado')).toBeVisible();
    await expect(statusCard(page, 'Servizo').getByText('Dexenerado', { exact: true })).toHaveCount(
      2,
    );
    await expect(
      statusCard(page, 'Base de datos').getByText('Non accesible', { exact: true }),
    ).toHaveCount(2);
  });
});
