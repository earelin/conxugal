import { expect, type Page, test } from '@playwright/test';

import { horizontalOverflow } from '../support/locators';
import { resetMappings } from '../support/wiremock';

const PLACEHOLDER = 'Escolle un órgano';
const SERGAS = 'Servizo Galego de Saúde (SERGAS)';
const UNCLASSIFIED = 'Instituto Galego da Vivenda e Solo';
const INACTIVE = 'Hospital Álvaro Cunqueiro';
const INACTIVE_ID = '5c3e8a70-9b41-4f06-d285-4a0c7e3b1f59';
const SERGAS_ID = '7e5a0c92-1d63-4b28-f407-6c2e9a5d3b71';

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

    await expect(page).toHaveURL(new RegExp(`/organo/${SERGAS_ID}$`));
    // The contracts page does not exist yet, so the shell renders its
    // not-found body — the picker's job ends at the URL.
    await expect(page.getByRole('button', { name: `Órgano ${SERGAS}` })).toBeVisible();
  });

  test('opens an Órgano from the keyboard alone, and closes on Escape', async ({ page }) => {
    await trigger(page).focus();
    await page.keyboard.press('Enter');

    // The dropdown takes focus, so the tree is reachable without tabbing past
    // the rest of the panel first.
    await expect(tree(page)).toBeVisible();
    await expect(tree(page).getByRole('treeitem').first()).toBeFocused();

    await page.keyboard.press('Escape');
    await expect(tree(page)).toBeHidden();
    await expect(trigger(page)).toBeFocused();

    await page.keyboard.press('Enter');
    await expect(tree(page).getByRole('treeitem').first()).toBeFocused();

    // Five rows down is the inactive Órgano filed under Consellería de
    // Sanidade, which is as selectable from the keyboard as any other.
    for (let step = 0; step < 5; step += 1) {
      await page.keyboard.press('ArrowDown');
    }
    await expect(page.locator('[role=treeitem]:focus')).toHaveAttribute(
      'data-value',
      `organo:${INACTIVE_ID}`,
    );

    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(new RegExp(`/organo/${INACTIVE_ID}$`));
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
