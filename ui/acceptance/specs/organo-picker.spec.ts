import { expect, type Page, test } from '@playwright/test';

import { resetMappings } from '../support/wiremock';

const PLACEHOLDER = 'Escolle un órgano';
const SERGAS = 'Servizo Galego de Saúde (SERGAS)';
const UNCLASSIFIED = 'Instituto Galego da Vivenda e Solo';
const INACTIVE = 'Hospital Álvaro Cunqueiro';

test.beforeEach(async ({ page }) => {
  await resetMappings();
  await page.goto('/');
});

test.afterAll(async () => {
  await resetMappings();
});

function trigger(page: Page) {
  return page.getByRole('button', { name: `Órgano ${PLACEHOLDER}` });
}

function tree(page: Page) {
  return page.getByRole('tree', { name: 'Órgano' });
}

async function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
}

test.describe('Órgano picker', () => {
  test('drops down the browse tree from the side panel of any page', async ({ page }) => {
    await trigger(page).click();

    await expect(tree(page).getByText('Consellerías')).toBeVisible();
    // An unclassified Órgano sits at the root, and an inactive one holding
    // visible contracts stays in its term.
    await expect(tree(page).getByText(UNCLASSIFIED)).toBeVisible();
    await expect(tree(page).getByText(INACTIVE)).toBeVisible();
    await expect(tree(page).getByText(SERGAS)).toBeVisible();
  });

  test('shows less than the administration area, pruning the terms left empty', async ({
    page,
  }) => {
    await trigger(page).click();

    // Both terms hold Órganos the administration area lists, and neither holds
    // one of the visible set.
    await expect(tree(page).getByText('Concellos')).toBeHidden();
    await expect(tree(page).getByText('Deputacións provinciais')).toBeHidden();
    // A term whose own Órganos are all absent but whose descendant has one.
    await expect(tree(page).getByText('Consellería de Educación')).toBeVisible();

    await page.goto('/administracion/organos');
    await expect(page.getByRole('tree', { name: 'Taxonomía' })).toContainText('Concellos');
    await expect(page.getByRole('tree', { name: 'Taxonomía' })).toContainText(
      'Deputacións provinciais',
    );
  });

  test('opens the chosen Órgano and names it once open', async ({ page }) => {
    await trigger(page).click();
    await tree(page).getByText(SERGAS).click();

    await expect(page).toHaveURL(/\/organo\/7e5a0c92-1d63-4b28-f407-6c2e9a5d3b71$/);
    // FEAT-0013 has not built that page yet, so the shell renders its
    // not-found body — the picker's job ends at the URL.
    await expect(page.getByRole('button', { name: `Órgano ${SERGAS}` })).toBeVisible();
  });

  test('offers no control that changes anything', async ({ page }) => {
    await trigger(page).click();

    await expect(tree(page).getByRole('button')).toHaveCount(0);
    await expect(tree(page).getByRole('link')).toHaveCount(0);
  });
});

test.describe('Órgano picker at 360 px', () => {
  test.use({ viewport: { width: 360, height: 720 } });

  test('opens from the collapsed navbar without pushing the page sideways', async ({ page }) => {
    await page.getByRole('button', { name: 'Alternar a navegación' }).click();
    await trigger(page).click();

    await expect(tree(page).getByText(SERGAS)).toBeVisible();
    expect(await horizontalOverflow(page)).toBe(0);
  });
});
