import { expect, type Page, test } from '@playwright/test';

import { degradedSystemStatus } from '../support/fixtures';
import { navLink } from '../support/locators';
import { resetMappings, stubJson } from '../support/wiremock';

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

    // Coarse runtime info, and the promise that it never carries secrets. Scoped
    // to the card: the metrics panel renders memory in the same format, so a
    // page-wide match could be satisfied by the wrong figure entirely.
    const systemInfo = statusCard(page, 'Información do sistema');
    await expect(systemInfo).toBeVisible();
    await expect(systemInfo.getByText('0.1.0-SNAPSHOT')).toBeVisible();
    await expect(systemInfo.getByText('25.0.1 (Eclipse Adoptium)')).toBeVisible();
    await expect(systemInfo.getByText('512 / 1024 MB')).toBeVisible();
    await expect(systemInfo.getByText('Linux (amd64)')).toBeVisible();
    await expect(
      systemInfo.getByText(
        'A información de estado nunca inclúe credenciais nin cadeas de conexión.',
      ),
    ).toBeVisible();
  });

  test('reflects the datastore going unreachable rather than a cached healthy snapshot', async ({
    page,
  }) => {
    await page.goto('/administracion');
    await expect(page.getByText('Servizo operativo')).toBeVisible();

    await stubJson('GET', '/api/admin/system-status', degradedSystemStatus);

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

/** Each dashboard card is a group named after its own heading. */
function statusCard(page: Page, name: string) {
  return page.getByRole('group', { name, exact: true });
}
