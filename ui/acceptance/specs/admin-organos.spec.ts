import { expect, type Page, test } from '@playwright/test';

import { horizontalOverflow } from '../support/locators';
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

test.describe('Órganos section', () => {
  test('opens on the unclassified worklist and switches to a term', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Sen clasificar' })).toBeVisible();
    await expect(page.getByText('Instituto Galego da Vivenda e Solo')).toBeVisible();
    await expect(
      page.getByText('3 órganos sen clasificar · 0 marcados para importar'),
    ).toBeVisible();
    // Nothing stored and nothing asked for: the worklist's rows show the dash.
    await expect(page.getByText('—').first()).toBeVisible();

    await tree(page).getByText('Consellería de Sanidade').click();

    await expect(page.getByRole('heading', { name: 'Consellería de Sanidade' })).toBeVisible();
    await expect(page.getByText('Servizo Galego de Saúde (SERGAS)')).toBeVisible();
    await expect(page.getByText('3 órganos neste termo · 2 marcados para importar')).toBeVisible();
    await expect(page.getByText('Instituto Galego da Vivenda e Solo')).toBeHidden();

    // An inactive Órgano stays listed rather than being filtered out.
    await expect(page.getByText('Hospital Álvaro Cunqueiro')).toBeVisible();
    await expect(page.getByText('INACTIVO')).toBeVisible();

    // A half-loaded Órgano says so rather than reading as up to date.
    await expect(page.getByText('PARCIAL').first()).toBeVisible();
    // An inactive Órgano keeps its row with the switch blocked, saying why.
    await expect(page.getByText('Só activos')).toBeVisible();
    await expect(
      page.getByRole('switch', { name: 'Importar contratos menores: Hospital Álvaro Cunqueiro' }),
    ).toBeDisabled();
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
      // Matched whole and case-sensitively against the label the DOM holds —
      // the uppercase is a text-transform — because the toolbar's scope note
      // now carries "activos" and a loose match would find that instead.
      await expect(page.getByText('Activo', { exact: true })).toBeVisible();
      // So does the import-state badge the new column adds beside it.
      await expect(page.getByText('Importado', { exact: true })).toBeVisible();

      // Nothing is clipped inside a card either, which a page-level scroll check
      // cannot see: a wrapped two-line term name must not squeeze its count away.
      // What counts as clipped is text the reader cannot get to: an element that
      // hides its overflow and holds something to read. A region the reader can
      // scroll has lost nothing, and a control's own chrome — Mantine sizes a
      // Switch's track around a thumb that overruns it — was never text.
      const clipped = await page.evaluate(() =>
        Array.from(document.querySelectorAll('body *'))
          .filter((el) => el.clientWidth > 0 && el.scrollWidth > el.clientWidth + 1)
          .filter((el) => ['hidden', 'clip'].includes(getComputedStyle(el).overflowX))
          .filter((el) => (el.textContent ?? '').trim() !== '')
          .map((el) => el.className.toString()),
      );
      expect(clipped).toEqual([]);
    });
  });
});
