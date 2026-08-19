import { expect, type Page, test } from '@playwright/test';

import { horizontalOverflow } from '../support/locators';
import { resetMappings } from '../support/wiremock';

// The Órgano the picker opens on, and the one whose 2025 the stub gives a
// selection worth walking: seven visible contracts over pages of three.
const SERGAS_ID = '7e5a0c92-1d63-4b28-f407-6c2e9a5d3b71';
// Partial and no longer updating, so both of the section's statements stand
// beside a paged list.
const PARTIAL_ID = '5c3e8a70-9b41-4f06-d285-4a0c7e3b1f59';
// Complete but no longer updating — the second statement without the first.
const IDLE_ID = '1d8e2a94-5b63-4f27-9c14-6a3f8e5d0c71';
// Holds no visible contrato menor of any family, which is what an Órgano whose
// contracts are all anomalous looks like: indistinguishable from one that
// awarded none.
const ANOMALOUS_ONLY_ID = '0b6d1f52-3a47-4c19-8e25-7f1c4d9a2b60';

const sectionPath = (organoId: string) => `/organo/${organoId}/contratos-menores`;

// What the stub serves for 2025: seven contracts over three pages of three, so
// the count is not a multiple of the page size and the last page is short. The
// largest of the year is the one published earliest, which is what makes
// «amount, largest first» observably different from the first page of the
// default ordering rather than a reshuffle of it.
const TOTAL_ITEMS = 7;
const TOTAL_PAGES = 3;
const PAGE_SIZE = 3;
const LARGEST_OF_THE_YEAR = '1142208';
// The second page of «importe, maior primeiro», which is what a URL copied from
// a non-default selection has to reopen on.
const AMOUNT_DESC_PAGE_2 = ['1179004', '1149330', '1160245'];

const YEAR_LABEL = 'Ano';
const SORT_LABEL = 'Ordenar por';
const SORT_DATE_DESC = 'Data de publicación (máis recente primeiro)';
const SORT_DATE_ASC = 'Data de publicación (máis antiga primeiro)';
const SORT_AMOUNT_DESC = 'Importe (maior primeiro)';
const SORT_AMOUNT_ASC = 'Importe (menor primeiro)';
const EVERY_ORDERING = [SORT_DATE_DESC, SORT_DATE_ASC, SORT_AMOUNT_DESC, SORT_AMOUNT_ASC];

const TABLE_LABEL = 'Contratos menores deste órgano';
const PAGING_LABEL = 'Paxinación';
const FIRST = 'Primeira';
const PREVIOUS = 'Anterior';
const NEXT = 'Seguinte';
const LAST = 'Última';
const RECORDS = `${TOTAL_ITEMS} rexistros`;
const OF_PAGES = `de ${TOTAL_PAGES}`;

const PARTIAL_TITLE = 'Importación en curso';
const NOT_UPDATED_TITLE = 'Este órgano xa non se actualiza';
const NO_CONTRACTS = 'Non hai contratos para este órgano.';

function yearChooser(page: Page) {
  return page.getByRole('combobox', { name: YEAR_LABEL });
}

function sortChooser(page: Page) {
  return page.getByRole('combobox', { name: SORT_LABEL });
}

function pagingControl(page: Page) {
  return page.getByRole('navigation', { name: PAGING_LABEL });
}

function pagingButton(page: Page, name: string) {
  return pagingControl(page).getByRole('button', { name });
}

/** The jump box, which is also where the page in force is shown as a number. */
function pageBox(page: Page) {
  return pagingControl(page).getByRole('textbox');
}

/** The wrapper the rows are marked busy on while the next page is fetched. */
function tableRegion(page: Page) {
  return page
    .locator('[aria-busy]')
    .filter({ has: page.getByRole('table', { name: TABLE_LABEL }) });
}

/**
 * The contracts on screen, by the source identifier each row states. Identified
 * by its shape rather than by a formatted date or amount: those are rendered in
 * a locale whose data differs between browser builds, and a spec must not depend
 * on it.
 */
function shownSourceIds(page: Page) {
  return page
    .getByRole('table', { name: TABLE_LABEL })
    .getByText(/^\d{7}$/)
    .allTextContents();
}

async function choose(page: Page, chooser: ReturnType<typeof yearChooser>, option: string) {
  await chooser.click();
  await page.getByRole('option', { name: option, exact: true }).click();
}

/** Waits for the page in force to be the one asked for, not merely for a table. */
async function showingPage(page: Page, asked: number) {
  await expect(pageBox(page)).toHaveValue(String(asked));
}

test.beforeEach(async () => {
  await resetMappings();
});

test.afterAll(async () => {
  await resetMappings();
});

test.describe('Browsing an Órgano’s contratos menores', () => {
  test('walks a year whose count is not a multiple of the page size, end to end', async ({
    page,
  }) => {
    await page.goto(sectionPath(SERGAS_ID));
    await showingPage(page, 1);

    // The two ends are disabled rather than hidden, so the control never changes
    // shape under a reader.
    await expect(pagingButton(page, FIRST)).toBeDisabled();
    await expect(pagingButton(page, PREVIOUS)).toBeDisabled();
    await expect(pagingControl(page).getByText(RECORDS)).toBeVisible();
    await expect(pagingControl(page).getByText(OF_PAGES)).toBeVisible();

    const walked = [...(await shownSourceIds(page))];
    await pagingButton(page, NEXT).click();
    await showingPage(page, 2);
    walked.push(...(await shownSourceIds(page)));
    await pagingButton(page, NEXT).click();
    await showingPage(page, 3);
    walked.push(...(await shownSourceIds(page)));

    // Every contract of the year seen exactly once: none repeated, none skipped.
    expect(walked).toHaveLength(TOTAL_ITEMS);
    expect(new Set(walked).size).toBe(TOTAL_ITEMS);
    // Ending on a short page is what a count that is not a multiple of the page
    // size ends on, and is why this year is the one walked.
    expect(await shownSourceIds(page)).toHaveLength(TOTAL_ITEMS % PAGE_SIZE);
    await expect(pagingButton(page, NEXT)).toBeDisabled();
    await expect(pagingButton(page, LAST)).toBeDisabled();
  });

  test('changes neither the stated count, the page total nor the ordering while paging', async ({
    page,
  }) => {
    await page.goto(`${sectionPath(SERGAS_ID)}?sort=amount%2Cdesc`);
    await showingPage(page, 1);
    await expect(sortChooser(page)).toHaveValue(SORT_AMOUNT_DESC);

    await pagingButton(page, LAST).click();
    await showingPage(page, TOTAL_PAGES);

    // Paging is a window onto the selection, not a change to it.
    await expect(pagingControl(page).getByText(RECORDS)).toBeVisible();
    await expect(pagingControl(page).getByText(OF_PAGES)).toBeVisible();
    await expect(sortChooser(page)).toHaveValue(SORT_AMOUNT_DESC);
    await expect(yearChooser(page)).toHaveValue('2025');
  });

  test('carries the whole selection in the URL, and walks paging history back', async ({
    page,
  }) => {
    // Started from a selection that is nobody's default, so the copied URL below
    // carries all three: an absent parameter is deliberately left absent, so a
    // link copied from the defaults would prove only that the defaults are the
    // defaults.
    await page.goto(`${sectionPath(SERGAS_ID)}?year=2025&sort=amount%2Cdesc`);
    await showingPage(page, 1);

    await pagingButton(page, NEXT).click();
    await showingPage(page, 2);
    const shared = new URL(page.url());
    expect(shared.searchParams.get('year')).toBe('2025');
    expect(shared.searchParams.get('sort')).toBe('amount,desc');
    expect(shared.searchParams.get('page')).toBe('2');

    await page.goBack();
    await showingPage(page, 1);

    // A copied URL reopens the same page of the same selection in the same
    // ordering — the response states no ordering, so the URL is what carries it.
    await page.goto(shared.toString());
    await showingPage(page, 2);
    await expect(yearChooser(page)).toHaveValue('2025');
    await expect(sortChooser(page)).toHaveValue(SORT_AMOUNT_DESC);
    expect(await shownSourceIds(page)).toEqual(AMOUNT_DESC_PAGE_2);
  });

  test('lands a page past the end on the last page, with the true count stated', async ({
    page,
  }) => {
    await page.goto(`${sectionPath(SERGAS_ID)}?page=9`);

    // Not an error and not an empty table: the API answers the request it was
    // given, and the client clamps.
    await showingPage(page, TOTAL_PAGES);
    await expect(page).toHaveURL(`${sectionPath(SERGAS_ID)}?page=3`);
    await expect(pagingControl(page).getByText(RECORDS)).toBeVisible();
    expect(await shownSourceIds(page)).toHaveLength(TOTAL_ITEMS % PAGE_SIZE);
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  test('clamps a year that holds a single page down to it', async ({ page }) => {
    // The other year the stub serves holds one page, so the clamp has to read
    // the totals of the selection actually asked for rather than of the one the
    // reader came from.
    await page.goto(`${sectionPath(SERGAS_ID)}?year=2024&page=4`);

    await showingPage(page, 1);
    expect(new URL(page.url()).searchParams.get('page')).toBe('1');
    await expect(pagingButton(page, NEXT)).toBeDisabled();
  });

  test('keeps the count and the page total on screen while the next page loads', async ({
    page,
  }) => {
    await page.goto(sectionPath(SERGAS_ID));
    await showingPage(page, 1);

    // Held open until this test lets go, rather than slowed by a delay the
    // assertions would then have to outrun: a web-first assertion retries for
    // five seconds, so against any finite delay every check below would pass by
    // waiting out the very state it is here to reject.
    let release = () => {};
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.route('**/contratos-menores?*', async (route) => {
      await held;
      await route.continue();
    });
    await pagingButton(page, NEXT).click();

    // Moving between pages changes neither number, so neither may leave the
    // screen while it happens — nor may the button just pressed lose focus.
    await expect(pagingControl(page).getByText(RECORDS)).toBeVisible();
    await expect(pagingControl(page).getByText(OF_PAGES)).toBeVisible();
    await expect(pagingButton(page, NEXT)).toBeFocused();
    await expect(tableRegion(page)).toHaveAttribute('aria-busy', 'true');

    release();
    await showingPage(page, 2);
    await expect(tableRegion(page)).toHaveAttribute('aria-busy', 'false');
  });

  test('waits rather than stating the old count when the year changes', async ({ page }) => {
    await page.goto(`${sectionPath(SERGAS_ID)}?page=3`);
    await showingPage(page, TOTAL_PAGES);

    let release = () => {};
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.route('**/contratos-menores?*', async (route) => {
      await held;
      await route.continue();
    });
    await choose(page, yearChooser(page), '2024');

    // A change of selection is a wait, not a window moving: the count, the page
    // total and the page in force are all about to change, so holding the old
    // ones on screen would be four surfaces disagreeing at once. 2024 holds two
    // records on one page.
    await expect(pagingControl(page)).toHaveCount(0);
    await expect(page.getByRole('table', { name: TABLE_LABEL })).toHaveCount(0);

    release();
    await showingPage(page, 1);
    await expect(pagingControl(page).getByText(RECORDS)).toHaveCount(0);
    await expect(pagingButton(page, NEXT)).toBeDisabled();
  });

  test('moves focus off the control that leads nowhere once it is reached', async ({ page }) => {
    await page.goto(sectionPath(SERGAS_ID));
    await showingPage(page, 1);

    await pagingButton(page, LAST).click();
    await showingPage(page, TOTAL_PAGES);

    // *Última* disables itself on arrival, and a disabled element cannot hold
    // focus — so without somewhere to send it the browser drops a keyboard
    // reader to the top of the document, from a button they pressed on purpose.
    await expect(pagingButton(page, LAST)).toBeDisabled();
    await expect(pageBox(page)).toBeFocused();
  });
});

test.describe('The ordering', () => {
  test('offers exactly four entries and nothing else', async ({ page }) => {
    await page.goto(sectionPath(SERGAS_ID));
    await showingPage(page, 1);

    await sortChooser(page).click();

    await expect(page.getByRole('option')).toHaveText(EVERY_ORDERING);
  });

  test('puts the largest contract of the whole year on the first page', async ({ page }) => {
    await page.goto(sectionPath(SERGAS_ID));
    await showingPage(page, 1);
    // It is on the *last* page of the default ordering, so finding it first here
    // cannot be the same rows in another arrangement.
    expect(await shownSourceIds(page)).not.toContain(LARGEST_OF_THE_YEAR);

    await choose(page, sortChooser(page), SORT_AMOUNT_DESC);

    // Polled rather than read once behind `showingPage`: this reordering never
    // leaves page 1, so the page in force says nothing about whether the new one
    // has arrived, and reading the rows would race the refetch.
    await expect.poll(async () => (await shownSourceIds(page))[0]).toBe(LARGEST_OF_THE_YEAR);
    expect(new URL(page.url()).searchParams.get('sort')).toBe('amount,desc');
  });

  test('opens on the default rather than an error when the URL names one that is not offered', async ({
    page,
  }) => {
    await page.goto(`${sectionPath(SERGAS_ID)}?sort=obxecto%2Cdesc`);

    await showingPage(page, 1);
    await expect(sortChooser(page)).toHaveValue(SORT_DATE_DESC);
    expect(new URL(page.url()).searchParams.get('sort')).toBe('publicationDate,desc');
  });
});

test.describe('Changing the selection while deep in it', () => {
  test('returns to the first page when the year changes', async ({ page }) => {
    await page.goto(`${sectionPath(SERGAS_ID)}?page=3`);
    await showingPage(page, TOTAL_PAGES);

    await choose(page, yearChooser(page), '2024');

    await showingPage(page, 1);
    const asked = new URL(page.url()).searchParams;
    expect(asked.get('year')).toBe('2024');
    // Page 3 counted from a selection that no longer exists.
    expect(asked.has('page')).toBe(false);
  });

  test('returns to the first page when the ordering changes', async ({ page }) => {
    await page.goto(`${sectionPath(SERGAS_ID)}?page=3`);
    await showingPage(page, TOTAL_PAGES);

    await choose(page, sortChooser(page), SORT_AMOUNT_ASC);

    await showingPage(page, 1);
    const asked = new URL(page.url()).searchParams;
    expect(asked.get('sort')).toBe('amount,asc');
    expect(asked.has('page')).toBe(false);
  });

  test('returns to the first page when only the direction changes', async ({ page }) => {
    await page.goto(`${sectionPath(SERGAS_ID)}?page=2`);
    await showingPage(page, 2);

    await choose(page, sortChooser(page), SORT_DATE_ASC);

    await showingPage(page, 1);
    expect(new URL(page.url()).searchParams.has('page')).toBe(false);
  });
});

test.describe('What the section says about itself, beside a paged list', () => {
  test('states that what is shown is incomplete, and that the Órgano is no longer updated', async ({
    page,
  }) => {
    await page.goto(sectionPath(PARTIAL_ID));

    await showingPage(page, 1);
    // Two independent statements, both true of an Órgano unmarked halfway
    // through its initial import — and neither replaced by the list beneath.
    await expect(page.getByText(PARTIAL_TITLE)).toBeVisible();
    await expect(page.getByText(NOT_UPDATED_TITLE)).toBeVisible();
    await expect(pagingControl(page).getByText(RECORDS)).toBeVisible();
  });

  test('says only that the Órgano is no longer updated when its import has finished', async ({
    page,
  }) => {
    await page.goto(sectionPath(IDLE_ID));

    await expect(yearChooser(page)).toBeVisible();
    await expect(page.getByText(NOT_UPDATED_TITLE)).toBeVisible();
    await expect(page.getByText(PARTIAL_TITLE)).toHaveCount(0);
  });

  test('says neither for an Órgano that is complete and still being refreshed', async ({
    page,
  }) => {
    await page.goto(sectionPath(SERGAS_ID));

    await showingPage(page, 1);
    await expect(page.getByText(PARTIAL_TITLE)).toHaveCount(0);
    await expect(page.getByText(NOT_UPDATED_TITLE)).toHaveCount(0);
  });

  test('states a count the reader has no other view of', async ({ page }) => {
    await page.goto(sectionPath(SERGAS_ID));
    await showingPage(page, 1);

    // The Órgano holds more contratos menores than this: the anomalous ones are
    // withheld from every page *and from this count*, so the stated figure is
    // the only thing that says how many the year holds that can be shown.
    await expect(pagingControl(page).getByText(RECORDS)).toBeVisible();
    expect(await shownSourceIds(page)).toHaveLength(PAGE_SIZE);
  });

  test('presents no section at all for an Órgano whose contracts are all anomalous', async ({
    page,
  }) => {
    await page.goto(`/organo/${ANOMALOUS_ONLY_ID}`);

    // Indistinguishable from one that awarded none: no tab, no chooser, no
    // paging control, and no hint that anything was withheld.
    await expect(page.getByText(NO_CONTRACTS)).toBeVisible();
    await expect(yearChooser(page)).toHaveCount(0);
    await expect(pagingControl(page)).toHaveCount(0);
    await expect(page.getByRole('table', { name: TABLE_LABEL })).toHaveCount(0);
  });
});

test.describe('at a 360 px viewport', () => {
  test.use({ viewport: { width: 360, height: 780 } });

  // Sized before the page loads rather than resized once it has, for the reason
  // `organo-page.spec.ts` records: resizing mid-test races the AppShell's reflow.
  test('wraps the two choosers and the paging control rather than squeezing them', async ({
    page,
  }) => {
    await page.goto(sectionPath(SERGAS_ID));
    await showingPage(page, 1);

    // The ordering's entries are whole Galician sentences and the control is
    // five buttons wide, so both are what a narrow column would push sideways.
    await expect(yearChooser(page)).toBeInViewport({ ratio: 1 });
    await expect(sortChooser(page)).toBeInViewport({ ratio: 1 });
    // Scrolled to first: the control sits below a table three rows deep, so at
    // this height it is off the *bottom* of the viewport, and `toBeInViewport`
    // would report that rather than the width this case is about.
    await pagingButton(page, LAST).scrollIntoViewIfNeeded();
    await expect(pagingButton(page, FIRST)).toBeInViewport({ ratio: 1 });
    await expect(pagingButton(page, LAST)).toBeInViewport({ ratio: 1 });
    // The table itself may scroll sideways — it has its own region for that —
    // but the page may not.
    expect(await horizontalOverflow(page)).toBe(0);
  });
});
