import { expect, test } from '@playwright/test';

import { horizontalOverflow } from '../support/locators';
import { resetMappings } from '../support/wiremock';

// An Órgano the administration area lists and the picker withholds, because it
// holds no visible contract of any family — so this page is reached by a
// retained link and by nothing the UI offers, which is the only way that state
// exists. Every Órgano the picker *does* offer holds a family, and redirects to
// a child route no section is mounted into yet, so the tab bar is the component
// suite's to prove until one is.
const EMPTY_ID = '0b6d1f52-3a47-4c19-8e25-7f1c4d9a2b60';
const EMPTY_NAME = 'Axencia de Turismo de Galicia';
const UNKNOWN_ID = '0f0e0d0c-0b0a-4009-8008-070605040302';

const NO_CONTRACTS = 'Non hai contratos para este órgano.';
const NO_CONTRACTS_HELP = 'Non hai ningún contrato deste órgano que se poida amosar aquí.';
const NOT_FOUND_TITLE = 'Non atopamos este órgano.';
const NOT_FOUND_HELP =
  'A ligazón pode estar mal, ou o órgano pode xa non existir no catálogo. Escolle un ' +
  'órgano no selector do panel lateral para seguir.';
const RETRY = 'Tentar de novo';

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

  // Only the state without a bar and without an outlet is reachable here, which
  // is the narrow half of this criterion: the width that will matter is a page
  // with tabs, and that check belongs with the task that mounts the first
  // section into them.
  test('reads at 360 px without pushing the page sideways', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 720 });
    await page.goto(`/organo/${EMPTY_ID}`);

    await expect(page.getByText(NO_CONTRACTS_HELP)).toBeVisible();
    expect(await horizontalOverflow(page)).toBe(0);
  });
});
