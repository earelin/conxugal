import { expect, type Page, test } from '@playwright/test';

import { resetMappings } from '../support/wiremock';

const DEEPEST_TERM = 'Axencias e entidades instrumentais';
const LONG_PARENT = 'Consellería de Educación, Ciencia, Universidades e FP';

test.beforeEach(async ({ page }) => {
  await resetMappings();
  await page.goto('/administracion/organos');
  await expect(page.getByRole('heading', { name: 'Órganos', level: 2 })).toBeVisible();
});

test.afterAll(async () => {
  await resetMappings();
});

function tree(page: Page) {
  return page.getByRole('tree', { name: 'Taxonomía' });
}

async function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
}

test.describe('Órganos section', () => {
  test('opens on the unclassified worklist and switches to a term', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Sen clasificar' })).toBeVisible();
    await expect(page.getByText('Instituto Galego da Vivenda e Solo')).toBeVisible();
    await expect(page.getByText('3 órganos sen clasificar')).toBeVisible();

    await tree(page).getByText('Consellería de Sanidade').click();

    await expect(page.getByRole('heading', { name: 'Consellería de Sanidade' })).toBeVisible();
    await expect(page.getByText('Servizo Galego de Saúde (SERGAS)')).toBeVisible();
    await expect(page.getByText('3 órganos neste termo')).toBeVisible();
    await expect(page.getByText('Instituto Galego da Vivenda e Solo')).toBeHidden();

    // An inactive Órgano stays listed rather than being filtered out.
    await expect(page.getByText('Hospital Álvaro Cunqueiro')).toBeVisible();
    await expect(page.getByText('INACTIVO')).toBeVisible();
  });

  test('selects a term with the keyboard alone', async ({ page }) => {
    await tree(page).getByRole('treeitem').first().focus();
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');

    await expect(page.getByRole('heading', { name: 'Sen clasificar' })).toBeHidden();
  });

  // Sized before the section loads rather than resized once it has. Resizing
  // mid-test measures the AppShell while it is still reacting to the new
  // viewport — the navbar offset is dropped a frame later — so the first
  // overflow check races that reflow instead of describing the layout. Loading
  // at 360 px is also the scenario the criterion is about.
  test.describe('at a 360 px viewport', () => {
    test.use({ viewport: { width: 360, height: 780 } });

    test('stays within the viewport, including the deepest breadcrumb', async ({ page }) => {
      // Both panes are reachable: they stack rather than overflow.
      await expect(tree(page)).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Sen clasificar' })).toBeVisible();
      expect(await horizontalOverflow(page)).toBe(0);

      // The worst case for the breadcrumb, whose crumbs cannot wrap: the deepest
      // term, sitting under the longest-named parent. The tree opens expanded, so
      // it is already reachable — clicking the parent would collapse it.
      await expect(tree(page).getByText(LONG_PARENT)).toBeVisible();
      await tree(page).getByText(DEEPEST_TERM).click();

      await expect(page.getByRole('heading', { name: DEEPEST_TERM })).toBeVisible();
      expect(await horizontalOverflow(page)).toBe(0);

      // The state badge stays readable rather than being ellipsised to "A…".
      await expect(page.getByText('ACTIVO')).toBeVisible();

      // Nothing is clipped inside a card either, which a page-level scroll check
      // cannot see: a wrapped two-line term name must not squeeze its count away.
      const clipped = await page.evaluate(() =>
        Array.from(document.querySelectorAll('body *'))
          .filter((el) => el.clientWidth > 0 && el.scrollWidth > el.clientWidth + 1)
          .map((el) => el.className.toString()),
      );
      expect(clipped).toEqual([]);
    });
  });
});
