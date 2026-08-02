import { expect, type Page, test } from '@playwright/test';

import { nonAdminSession } from '../support/fixtures';
import { nav, navLink } from '../support/locators';
import { resetMappings, stubJson } from '../support/wiremock';

test.beforeEach(async () => {
  await resetMappings();
});

test.afterAll(async () => {
  await resetMappings();
});

test.describe('Administration area access', () => {
  test('offers the administration section to an administrator', async ({ page }) => {
    await page.goto('/');
    await waitForSession(page);

    await expect(nav(page).getByText('Administración')).toBeVisible();

    await navLink(page, 'Usuarios').click();

    await expect(page).toHaveURL(/\/administracion\/usuarios$/);
    await expect(page.getByRole('heading', { name: 'Xestión de usuarios' })).toBeVisible();
  });

  test('hides the administration area from a non-administrator', async ({ page }) => {
    await stubJson('GET', '/api/me', nonAdminSession);

    await page.goto('/');
    await waitForSession(page);

    await expect(navLink(page, 'Inicio')).toBeVisible();
    await expect(nav(page).getByText('Administración')).toHaveCount(0);

    // Reaching the route directly gets nothing either. This gate is cosmetic —
    // the server denies the admin endpoints regardless of what the SPA renders.
    await page.goto('/administracion');
    await expect(page.getByRole('heading', { name: 'Páxina non atopada' })).toBeVisible();
  });
});

/**
 * The account menu renders only once the session has loaded, so waiting on it
 * proves `/api/me` has resolved. Without it, an assertion that the admin
 * section is absent would pass simply by outrunning the request.
 */
async function waitForSession(page: Page) {
  await expect(page.getByRole('button', { name: 'Abrir o menú da conta' })).toBeVisible();
}
