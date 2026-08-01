import { expect, test, type Page } from '@playwright/test';
import { accounts, generatedPassword } from '../support/fixtures';
import { bodiesSentTo, resetMappings } from '../support/wiremock';

function rowFor(page: Page, email: string) {
  return page.getByRole('row').filter({ hasText: email });
}

// The enabled labels are substrings of the disabled ones — "Activada" of
// "Desactivada", "Activar" of "Desactivar" — and both getByText and the `name`
// option match on substrings, case-insensitively. Every assertion on this pair
// must therefore be exact, or a disabled account would satisfy it.
function enabledBadge(row: ReturnType<typeof rowFor>) {
  return row.getByText('Activada', { exact: true });
}

function toggleButton(row: ReturnType<typeof rowFor>, label: 'Activar' | 'Desactivar') {
  return row.getByRole('button', { name: label, exact: true });
}

test.beforeEach(async ({ page }) => {
  await resetMappings();
  await page.goto('/administracion/usuarios');
  await expect(page.getByRole('heading', { name: 'Xestión de usuarios' })).toBeVisible();
});

test.afterAll(async () => {
  await resetMappings();
});

test.describe('User administration', () => {
  test('lists every account with its role, state and login dates', async ({ page }) => {
    const table = page.getByRole('table', { name: 'Xestión de usuarios' });

    for (const header of ['Persoa', 'Rol', 'Estado', 'Creación', 'Último acceso', 'Accións']) {
      await expect(table.getByRole('columnheader', { name: header })).toBeVisible();
    }

    const admin = rowFor(page, accounts.admin.email);
    await expect(admin.getByText('Administradora')).toBeVisible();
    await expect(enabledBadge(admin)).toBeVisible();

    // A disabled account stays listed and offers re-enabling.
    const disabled = rowFor(page, accounts.disabledUser.email);
    await expect(disabled.getByText('Desactivada')).toBeVisible();
    await expect(toggleButton(disabled, 'Activar')).toBeVisible();

    // Never having logged in is rendered explicitly, not as an empty cell.
    await expect(rowFor(page, accounts.neverLoggedIn.email).getByText('Nunca')).toBeVisible();

    await expect(page.getByText('As contas nunca se eliminan; só se desactivan.')).toBeVisible();
  });

  test('creates an account and reveals the generated password once', async ({ page }) => {
    const newEmail = 'nova.persoa@conxugal.gal';
    const passwordField = { name: 'Contrasinal inicial' } as const;

    await page.getByRole('button', { name: 'Novo usuario' }).click();

    const modal = page.getByRole('dialog');
    await expect(modal.getByText('Novo usuario')).toBeVisible();

    await modal.getByRole('textbox', { name: /Correo electrónico/ }).fill(newEmail);
    await modal.getByRole('combobox', { name: /Rol/ }).click();
    await page.getByRole('option', { name: 'Administradora' }).click();
    await modal.getByRole('button', { name: 'Crear conta' }).click();

    // The admin is shown the password exactly once, with the warning that it
    // will not be shown again.
    await expect(modal.getByText('Conta creada')).toBeVisible();
    await expect(
      modal.getByText('Este contrasinal non se volverá amosar. Cópiao agora nun lugar seguro.'),
    ).toBeVisible();
    await expect(modal.getByRole('textbox', passwordField)).toHaveValue(generatedPassword);
    await expect(modal.getByRole('button', { name: 'Copiar' })).toBeVisible();

    // The form asks only for email and role — the server generates the password.
    expect(await bodiesSentTo('POST', '/api/admin/users')).toEqual([
      { email: newEmail, role: 'ADMIN' },
    ]);

    await modal.getByRole('button', { name: 'Feito' }).click();
    await expect(modal).toBeHidden();

    const added = rowFor(page, newEmail);
    await expect(added.getByText('Administradora')).toBeVisible();
    await expect(enabledBadge(added)).toBeVisible();
    await expect(added.getByText('Nunca')).toBeVisible();

    // Reopening offers a fresh form, not the password again. Asserted on the
    // field rather than the text: the value of an input is not page text, so a
    // text-based check here would pass even with the reveal still on screen.
    await page.getByRole('button', { name: 'Novo usuario' }).click();
    await expect(modal.getByRole('button', { name: 'Crear conta' })).toBeVisible();
    await expect(modal.getByRole('textbox', passwordField)).toHaveCount(0);
  });

  test('disables an account and re-enables it, keeping it listed throughout', async ({ page }) => {
    const row = rowFor(page, accounts.enabledUser.email);
    await expect(enabledBadge(row)).toBeVisible();

    await toggleButton(row, 'Desactivar').click();

    await expect(row.getByText('Desactivada')).toBeVisible();
    await expect(toggleButton(row, 'Activar')).toBeVisible();

    await toggleButton(row, 'Activar').click();

    await expect(enabledBadge(row)).toBeVisible();
    await expect(toggleButton(row, 'Desactivar')).toBeVisible();

    const toggles = `/api/admin/users/${accounts.enabledUser.id}/enabled`;
    expect(await bodiesSentTo('POST', toggles)).toEqual([{ enabled: false }, { enabled: true }]);
  });
});
