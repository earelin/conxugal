import { expect, type Locator, type Page, test } from '@playwright/test';

import { horizontalOverflow } from '../support/locators';
import { clearRequestJournal, requestCountFor, resetMappings } from '../support/wiremock';

// An Órgano the administration area lists and the picker withholds, because it
// holds no visible contract of any family — so this page is reached by a
// retained link and by nothing the UI offers, which is the only way that state
// exists.
const EMPTY_ID = '0b6d1f52-3a47-4c19-8e25-7f1c4d9a2b60';
const EMPTY_NAME = 'Axencia de Turismo de Galicia';
const UNKNOWN_ID = '0f0e0d0c-0b0a-4009-8008-070605040302';

// The Órgano the picker opens on, and the one every reader following the UI
// reaches: it holds contratos menores, so it draws the bar and fills the outlet.
const SERGAS_ID = '7e5a0c92-1d63-4b28-f407-6c2e9a5d3b71';
const SERGAS_NAME = 'Servizo Galego de Saúde (SERGAS)';
// Its summary is partial and no longer updating, which is what makes both of
// the section's statements visible inside the frame.
const PARTIAL_ID = '5c3e8a70-9b41-4f06-d285-4a0c7e3b1f59';
const PARTIAL_NAME = 'Hospital Álvaro Cunqueiro';

// The one family this build routes to, and the address it sits at. Spelled
// once so a spec naming a family route cannot drift from the router's segment.
const FAMILY_PATH = 'contratos-menores';
const sectionPath = (organoId: string) => `/organo/${organoId}/${FAMILY_PATH}`;

const TABS_LABEL = 'Familias de contratos';
const CONTRATOS_MENORES = 'Contratos menores';
const YEAR_LABEL = 'Ano';
const PARTIAL_TITLE = 'Importación en curso';
const NOT_UPDATED_TITLE = 'Este órgano xa non se actualiza';

const NO_CONTRACTS = 'Non hai contratos para este órgano.';
const NO_CONTRACTS_HELP = 'Non hai ningún contrato deste órgano que se poida amosar aquí.';
const NOT_FOUND_TITLE = 'Non atopamos este órgano.';
const NOT_FOUND_HELP =
  'A ligazón pode estar mal, ou o órgano pode xa non existir no catálogo. Escolle un ' +
  'órgano no selector do panel lateral para seguir.';
const RETRY = 'Tentar de novo';
// The side-panel picker, named as `organo-picker.spec.ts` names it: the control
// is labelled by its field and its placeholder together.
const PICKER_PLACEHOLDER = 'Escolle un órgano';

// Named the way `organo-picker.spec.ts` names its own: the roles and Galician
// labels a reader reaches these controls by, spelled once.
function pickerTrigger(page: Page) {
  return page.getByRole('button', { name: `Órgano ${PICKER_PLACEHOLDER}` });
}

function organoTree(page: Page) {
  return page.getByRole('tree', { name: 'Órgano' });
}

function familyTab(scope: Page | Locator) {
  return scope.getByRole('tab', { name: CONTRATOS_MENORES });
}

function tabBar(page: Page) {
  return page.getByRole('tablist', { name: TABS_LABEL });
}

/** The section's control, reached through the page — the composition under test. */
function yearChooser(page: Page) {
  return page.getByRole('combobox', { name: YEAR_LABEL });
}

function statement(page: Page, title: string) {
  return page.getByRole('status').filter({ hasText: title });
}

test.beforeEach(async () => {
  await resetMappings();
});

test.afterAll(async () => {
  await resetMappings();
});

test.describe('Órgano page', () => {
  test('names an Órgano that holds nothing and says so, drawing no tab bar', async ({ page }) => {
    await page.goto(`/organo/${EMPTY_ID}`);

    await expect(page.getByRole('heading', { name: EMPTY_NAME })).toBeVisible();
    await expect(page.getByText(NO_CONTRACTS)).toBeVisible();
    await expect(page.getByText(NO_CONTRACTS_HELP)).toBeVisible();
    // An answer, not a failure: nothing to open and nothing to try again.
    await expect(page.getByRole('tablist')).toHaveCount(0);
    await expect(page.getByRole('button', { name: RETRY })).toHaveCount(0);
  });

  test('says an unknown Órgano was not found, offering nothing to try again', async ({ page }) => {
    await page.goto(`/organo/${UNKNOWN_ID}`);

    await expect(page.getByText(NOT_FOUND_TITLE)).toBeVisible();
    await expect(page.getByText(NOT_FOUND_HELP)).toBeVisible();
    await expect(page.getByRole('button', { name: RETRY })).toHaveCount(0);
    // A third thing again: an id that does not exist is not an Órgano holding
    // no contracts.
    await expect(page.getByText(NO_CONTRACTS)).toHaveCount(0);
  });

  // The narrow half of the criterion: a page with neither a bar nor an outlet.
  // The width that matters is the page as it ships, measured further down.
  test('reads at 360 px without pushing the page sideways', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 720 });
    await page.goto(`/organo/${EMPTY_ID}`);

    await expect(page.getByText(NO_CONTRACTS_HELP)).toBeVisible();
    expect(await horizontalOverflow(page)).toBe(0);
  });
});

test.describe('Órgano page with a family', () => {
  test('opens an Órgano from the side panel and shows that family of contracts', async ({
    page,
  }) => {
    await page.goto('/');
    await pickerTrigger(page).click();
    await organoTree(page).getByText(SERGAS_NAME).click();

    // The journey no earlier task could run end to end: the picker names an
    // Órgano, the page frames it, and that family's own section fills the outlet.
    await expect(page).toHaveURL(sectionPath(SERGAS_ID));
    await expect(page.getByRole('heading', { name: SERGAS_NAME })).toBeVisible();
    const tab = familyTab(tabBar(page));
    await expect(tab).toHaveAttribute('aria-selected', 'true');
    await expect(yearChooser(page)).toHaveValue('2025');
  });

  test('opens the same page from a link straight to the family', async ({ page }) => {
    await page.goto(sectionPath(SERGAS_ID));

    await expect(page.getByRole('heading', { name: SERGAS_NAME })).toBeVisible();
    await expect(familyTab(page)).toBeVisible();
    await expect(yearChooser(page)).toHaveValue('2025');
    // No redirect: the deep link is the address, not a detour through one.
    await expect(page).toHaveURL(sectionPath(SERGAS_ID));
  });

  test("carries the section's own selection in the query string beside the family", async ({
    page,
  }) => {
    // A page the selection actually holds. This case is about the redirect
    // carrying the three parameters across, and now that they mean something the
    // section reads them: 2023 holds one page in the stub, so naming a second
    // would be clamped to the last — correctly, and by the section rather than
    // by anything this page does.
    await page.goto(`/organo/${SERGAS_ID}?year=2023&sort=amount%2Cdesc&page=1`);

    await expect(yearChooser(page)).toHaveValue('2023');
    const url = new URL(page.url());
    expect(url.pathname).toBe(sectionPath(SERGAS_ID));
    expect(url.searchParams.get('year')).toBe('2023');
    expect(url.searchParams.get('sort')).toBe('amount,desc');
    expect(url.searchParams.get('page')).toBe('1');
  });

  test('lets the section speak for itself inside the page frame', async ({ page }) => {
    await page.goto(`/organo/${PARTIAL_ID}`);

    // Both statements are the section's and reached it as outlet context, which
    // is only observable once something is mounted in that outlet.
    await expect(page.getByText(PARTIAL_TITLE)).toBeVisible();
    await expect(page.getByText(NOT_UPDATED_TITLE)).toBeVisible();
    await expect(page.getByRole('heading', { name: PARTIAL_NAME })).toBeVisible();
    await expect(yearChooser(page)).toHaveValue('2025');
  });

  test('asks for the Órgano once, the section reading its summary from that answer', async ({
    page,
  }) => {
    await page.goto('/');
    await clearRequestJournal();
    await page.goto(`/organo/${SERGAS_ID}`);

    await expect(yearChooser(page)).toBeVisible();
    // One member read carries the name, the tab and the chooser's years. A
    // section fetching its own summary would show as a second call here.
    expect(await requestCountFor('GET', `/api/organo/${SERGAS_ID}`)).toBe(1);
  });

  // Sized before the page loads rather than resized once it has: resizing
  // mid-test measures the AppShell while it is still reacting to the new
  // viewport, so the overflow check races that reflow instead of describing the
  // layout. This is the first task that can measure the page as it ships — every
  // Órgano holding a family used to redirect past the frame to a route that did
  // not exist.
  test.describe('at a 360 px viewport', () => {
    test.use({ viewport: { width: 360, height: 780 } });

    test('frames the bar and the section without pushing the page sideways', async ({ page }) => {
      await page.goto(`/organo/${SERGAS_ID}`);

      await expect(familyTab(page)).toBeVisible();
      await expect(yearChooser(page)).toBeVisible();
      expect(await horizontalOverflow(page)).toBe(0);
    });

    // The Órgano whose section has something to wrap. SERGAS renders neither
    // statement, so its card holds one 180 px-wide chooser on a 328 px column
    // and cannot be squeezed by construction — measuring the width there proves
    // the frame and nothing the section puts in it.
    test('wraps the section’s own statements rather than squeezing them', async ({ page }) => {
      await page.goto(`/organo/${PARTIAL_ID}`);

      const partial = statement(page, PARTIAL_TITLE);
      const notUpdated = statement(page, NOT_UPDATED_TITLE);
      await expect(partial).toBeVisible();
      await expect(notUpdated).toBeVisible();

      // Every one of them whole and on screen: `toBeInViewport` fails on a
      // statement pushed outside the 360 px column, which a document-level
      // scroll check cannot see once an ancestor hides the overflow.
      await expect(partial).toBeInViewport({ ratio: 1 });
      await expect(notUpdated).toBeInViewport({ ratio: 1 });
      await expect(yearChooser(page)).toBeInViewport({ ratio: 1 });
      expect(await horizontalOverflow(page)).toBe(0);
    });
  });
});
