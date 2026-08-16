import { expect, test } from '@playwright/test';

import { horizontalOverflow } from '../support/locators';
import { resetMappings } from '../support/wiremock';

// The Órgano the visible set offers that holds no contract of any family. The
// ones that do hold a family redirect to a child route no section is mounted
// into yet, so the tab bar is proven by the component suite until it is.
const EMPTY_ID = '6d4f9b81-0c52-4a17-e396-5b1d8f4c2a60';
const EMPTY_NAME = 'Instituto Galego da Vivenda e Solo';
const UNKNOWN_ID = '0f0e0d0c-0b0a-4009-8008-070605040302';

const NO_CONTRACTS = 'Non hai contratos para este órgano.';
const NO_CONTRACTS_HELP = 'O sistema non garda ningún contrato deste órgano, en ningunha familia.';
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

  test('reads at 360 px without pushing the page sideways', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 720 });
    await page.goto(`/organo/${EMPTY_ID}`);

    await expect(page.getByText(NO_CONTRACTS_HELP)).toBeVisible();
    expect(await horizontalOverflow(page)).toBe(0);
  });
});
