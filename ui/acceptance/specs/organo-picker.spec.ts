import { expect, type Page, test } from '@playwright/test';

import { horizontalOverflow } from '../support/locators';
import { clearRequestJournal, requestCountFor, resetMappings } from '../support/wiremock';

const PLACEHOLDER = 'Escolle un órgano';
const SEARCH = 'Buscar un órgano';
const SERGAS = 'Servizo Galego de Saúde (SERGAS)';
const UNCLASSIFIED = 'Instituto Galego da Vivenda e Solo';
const INACTIVE = 'Hospital Álvaro Cunqueiro';
const INACTIVE_ID = '5c3e8a70-9b41-4f06-d285-4a0c7e3b1f59';
const SERGAS_ID = '7e5a0c92-1d63-4b28-f407-6c2e9a5d3b71';
// Listed by the administration area and outside the visible set, so no name
// reaches it from the picker in either of the control's two states.
const WITHHELD = 'Axencia de Turismo de Galicia';

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

function searchBox(page: Page) {
  return page.getByRole('textbox', { name: SEARCH });
}

/** The filter's matches, which share the tree's accessible name. */
function offered(page: Page) {
  return page.getByRole('listbox', { name: 'Órgano' });
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

    // The Órgano's page redirects on to the first family it holds, so the
    // segment beyond the id is the page's business — the picker's job ends at
    // the Órgano.
    await expect(page).toHaveURL(new RegExp(`/organo/${SERGAS_ID}(/|$)`));
    await expect(page.getByRole('button', { name: `Órgano ${SERGAS}` })).toBeVisible();
  });

  test('opens an Órgano from the keyboard alone, and closes on Escape', async ({ page }) => {
    await trigger(page).focus();
    await page.keyboard.press('Enter');

    // The dropdown takes focus, so the filter is reachable without tabbing past
    // the rest of the panel first, and the tree is one Tab beyond it.
    await expect(tree(page)).toBeVisible();
    await expect(searchBox(page)).toBeFocused();

    await page.keyboard.press('Escape');
    await expect(tree(page)).toBeHidden();
    await expect(trigger(page)).toBeFocused();

    await page.keyboard.press('Enter');
    await expect(searchBox(page)).toBeFocused();
    await page.keyboard.press('Tab');
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
    await expect(page).toHaveURL(new RegExp(`/organo/${INACTIVE_ID}(/|$)`));
  });

  test('offers no control that changes anything', async ({ page }) => {
    await trigger(page).click();

    await expect(tree(page).getByRole('button')).toHaveCount(0);
    await expect(tree(page).getByRole('link')).toHaveCount(0);
  });
});

test.describe('Órgano picker search', () => {
  test('offers a match as the reader types, ignoring case and accents', async ({ page }) => {
    await trigger(page).click();
    // «saude» is both unaccented and a fragment inside the stored name.
    await searchBox(page).fill('saude');

    await expect(offered(page).getByText(SERGAS)).toBeVisible();
    await expect(offered(page).getByText(UNCLASSIFIED)).toBeHidden();
    await expect(tree(page)).toBeHidden();

    await offered(page).getByText(SERGAS).click();
    await expect(page).toHaveURL(new RegExp(`/organo/${SERGAS_ID}(/|$)`));
  });

  test('states that an offered Órgano is inactive', async ({ page }) => {
    await trigger(page).click();
    await searchBox(page).fill('alvaro');

    await expect(offered(page).getByRole('option', { name: `${INACTIVE} Inactivo` })).toBeVisible();
  });

  test('withholds by name whatever the tree withholds', async ({ page }) => {
    await trigger(page).click();
    await searchBox(page).fill('turismo');

    await expect(page.getByText('Ningún órgano coincide con «turismo».')).toBeVisible();
    await expect(offered(page)).toBeHidden();

    // The other direction of the same criterion: the tree does not offer it
    // either, unclassified rows at its root included. Asserting only the search
    // half would pass while the two disagreed, which is the drift they exist to
    // rule out.
    await searchBox(page).fill('');
    await expect(tree(page).getByText(SERGAS)).toBeVisible();
    await expect(tree(page).getByText(WITHHELD)).toBeHidden();

    // The administration area lists it throughout, so the picker is withholding
    // it rather than the catalogue lacking it.
    await page.goto('/administracion/organos');
    await expect(page.getByRole('table')).toContainText(WITHHELD);
  });

  test('shows the tree for a whitespace-only query, and says so for one matching nothing', async ({
    page,
  }) => {
    await trigger(page).click();
    await searchBox(page).fill('   ');

    await expect(tree(page).getByText(SERGAS)).toBeVisible();
    await expect(page.getByText('Ningún órgano coincide', { exact: false })).toBeHidden();

    await searchBox(page).fill('sanidde');

    await expect(page.getByText('Ningún órgano coincide con «sanidde».')).toBeVisible();
    await expect(
      page.getByText('Revisa a busca ou baléiraa para ver a árbore completa.'),
    ).toBeVisible();
    await expect(tree(page)).toBeHidden();
    await expect(offered(page)).toBeHidden();
  });

  test('asks the server for nothing while the reader types', async ({ page }) => {
    await trigger(page).click();
    await expect(tree(page)).toBeVisible();
    await clearRequestJournal();

    await searchBox(page).pressSequentially('vivenda');

    await expect(offered(page).getByText(UNCLASSIFIED)).toBeVisible();
    expect(await requestCountFor('GET', '/api/organos')).toBe(0);
    expect(await requestCountFor('GET', '/api/organos/taxonomia')).toBe(0);
  });

  test('keeps Tab inside the dropdown after the arrows have moved focus', async ({ page }) => {
    await trigger(page).click();
    await searchBox(page).fill('a');
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('ArrowDown');

    // The tab stop travels with the focus, which is what lets the trap round
    // the dropdown recognise the focused match as the last thing in it. Left
    // behind, Tab walks out of the document and Escape — bound on the dropdown
    // — stops answering, stranding the reader with the picker still open.
    await page.keyboard.press('Tab');

    await expect(searchBox(page)).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(offered(page)).toBeHidden();
    await expect(trigger(page)).toBeFocused();
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

  test('filters at the same width, the longest name and all', async ({ page }) => {
    await page.getByRole('button', { name: 'Alternar a navegación' }).click();
    await trigger(page).click();
    await searchBox(page).fill('galego');

    await expect(offered(page).getByText(UNCLASSIFIED)).toBeVisible();
    expect(await horizontalOverflow(page)).toBe(0);
  });

  test('keeps the inactive marker readable beside a name that fills the row', async ({ page }) => {
    await page.getByRole('button', { name: 'Alternar a navegación' }).click();
    await trigger(page).click();
    // The one query offering an inactive Órgano; the row it draws is the
    // narrowest place the marker has to survive, sharing a nowrap row with a
    // name long enough to wrap.
    await searchBox(page).fill('hospital');

    const marker = offered(page).getByText('Inactivo');
    await expect(marker).toBeVisible();
    const box = await marker.boundingBox();
    expect(box?.width ?? 0).toBeGreaterThan(40);
    expect(await horizontalOverflow(page)).toBe(0);
  });
});
