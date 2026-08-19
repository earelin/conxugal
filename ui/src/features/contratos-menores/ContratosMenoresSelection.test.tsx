import { screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { formatCount } from '../../shared/lib/number';
import { counted } from '../../shared/lib/plural';
import type { ContratoMenor } from './contracts';
import {
  contract,
  contractsTable,
  copy,
  mockContracts,
  page,
  paging,
  pagingButton,
  pagingControl,
  renderSection,
  SECTION_PATH,
  sortChooser,
  summary,
  yearChooser,
} from './sectionHarness';

/**
 * A selection of seven over pages of three: not a multiple of the page size, so
 * the last page is short and a walk that miscounted anywhere would either repeat
 * a contract or skip one.
 */
const SIZE = 3;
const TOTAL_ITEMS = 7;
const TOTAL_PAGES = 3;

const everyContract: ContratoMenor[] = Array.from({ length: TOTAL_ITEMS }, (_, index) =>
  contract({ sourceId: 1100000 + index, amount: (index + 1) * 1000 }),
);

function pageOf(asked: number): ContratoMenor[] {
  return everyContract.slice((asked - 1) * SIZE, asked * SIZE);
}

/** One page of the seven, with the totals the whole selection has on every page. */
function mockPage(asked: number, sort?: string, delayMillis = 0) {
  return mockContracts(
    { year: 2025, page: asked, sort },
    200,
    page(pageOf(asked), {
      page: asked,
      size: SIZE,
      totalItems: TOTAL_ITEMS,
      totalPages: TOTAL_PAGES,
    }),
    delayMillis,
  );
}

async function chooseFrom(user: UserEvent, chooser: HTMLElement, option: string) {
  await user.click(chooser);
  await user.click(screen.getByRole('option', { name: option }));
}

function shownSourceIds(): string[] {
  return within(contractsTable())
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getByText(/^11\d{5}$/).textContent);
}

function statedCount() {
  return within(pagingControl()).getByText(counted(TOTAL_ITEMS, paging.records));
}

/**
 * What the section says aloud about the page on screen. Read from the live
 * region itself rather than by role: the two statements the section makes about
 * itself are the statuses here, and this must not be counted among them.
 */
function announced() {
  return document.querySelector('[aria-live="polite"]')?.textContent;
}

describe('sorting and paging over the selection', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  describe('the ordering control', () => {
    it('offers exactly the four orderings the API takes, and nothing else', async () => {
      const user = userEvent.setup();
      mockPage(1);
      renderSection(summary());

      await user.click(await screen.findByRole('combobox', { name: copy.sortLabel }));

      expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual([
        copy.sort.dateDesc,
        copy.sort.dateAsc,
        copy.sort.amountDesc,
        copy.sort.amountAsc,
      ]);
    });

    it('opens on the API’s own default when the URL names no ordering', async () => {
      mockPage(1);
      renderSection(summary());

      expect(await screen.findByRole('table')).toBeInTheDocument();
      expect(sortChooser()).toHaveValue(copy.sort.dateDesc);
    });

    it('opens on the ordering the URL already carries', async () => {
      mockPage(1, 'amount,desc');
      renderSection(summary(), `${SECTION_PATH}?sort=amount%2Cdesc`);

      expect(await screen.findByRole('table')).toBeInTheDocument();
      expect(sortChooser()).toHaveValue(copy.sort.amountDesc);
    });

    it('writes the chosen ordering as the API spells it, and asks for it', async () => {
      const user = userEvent.setup();
      mockPage(1);
      mockPage(1, 'amount,desc');
      const { router } = renderSection(summary());
      await screen.findByRole('table');

      await chooseFrom(user, sortChooser(), copy.sort.amountDesc);

      expect(router.state.location.search).toBe('?sort=amount%2Cdesc');
      // The interceptor is matched on the whole query, so it answering at all is
      // what proves the ordering reached the server.
      await waitFor(() => {
        expect(nock.pendingMocks()).toEqual([]);
      });
    });

    it('opens on the default rather than forwarding an ordering the API would refuse', async () => {
      // Sent on, it would be a 400 and an error page; the URL is corrected in
      // place instead, so the link a reader copies from here works.
      mockPage(1);
      const { router } = renderSection(summary(), `${SECTION_PATH}?sort=obxecto%2Cdesc`);

      expect(await screen.findByRole('table')).toBeInTheDocument();
      expect(sortChooser()).toHaveValue(copy.sort.dateDesc);
      await waitFor(() => {
        expect(router.state.location.search).toBe('?sort=publicationDate%2Cdesc');
      });
    });
  });

  describe('changing the selection returns the reader to the first page', () => {
    it('drops the page when the year changes', async () => {
      const user = userEvent.setup();
      mockPage(3);
      mockContracts({ year: 2024 }, 200, page([contract()]));
      const { router } = renderSection(summary(), `${SECTION_PATH}?page=3`);
      await screen.findByRole('table');

      await chooseFrom(user, yearChooser(), '2024');

      // Page 3 counted from a selection that no longer exists, so it is gone
      // rather than carried over.
      expect(new URLSearchParams(router.state.location.search).has('page')).toBe(false);
      expect(router.state.location.search).toBe('?year=2024');
    });

    it('drops the page when the ordering changes', async () => {
      const user = userEvent.setup();
      mockPage(3);
      mockPage(1, 'amount,asc');
      const { router } = renderSection(summary(), `${SECTION_PATH}?page=3`);
      await screen.findByRole('table');

      await chooseFrom(user, sortChooser(), copy.sort.amountAsc);

      const search = new URLSearchParams(router.state.location.search);
      expect(search.has('page')).toBe(false);
      expect(search.get('sort')).toBe('amount,asc');
    });
  });

  describe('the paging control', () => {
    it('states the whole selection’s count and page total, not the page’s', async () => {
      mockPage(1);
      renderSection(summary());
      await screen.findByRole('table');

      // Three rows on screen, seven stated: the count answers how many contracts
      // the year holds, which is a question of its own rather than an
      // intermediate value.
      expect(shownSourceIds()).toHaveLength(SIZE);
      expect(statedCount()).toBeInTheDocument();
      expect(
        within(pagingControl()).getByText(paging.ofPages(formatCount(TOTAL_PAGES))),
      ).toBeInTheDocument();
    });

    it('walks the whole selection, repeating none and skipping none', async () => {
      const user = userEvent.setup();
      mockPage(1);
      mockPage(2);
      mockPage(3);
      renderSection(summary());
      await screen.findByRole('table');

      const walked = [...shownSourceIds()];
      await user.click(pagingButton(paging.next));
      await waitFor(() => {
        expect(shownSourceIds()).not.toEqual(walked);
      });
      walked.push(...shownSourceIds());
      await user.click(pagingButton(paging.next));
      await waitFor(() => {
        expect(pagingButton(paging.next)).toBeDisabled();
      });
      walked.push(...shownSourceIds());

      expect(walked).toEqual(everyContract.map((each) => String(each.sourceId)));
      // A short last page is what a count that is not a multiple of the page
      // size ends on, and walking it is the whole point of this case.
      expect(shownSourceIds()).toHaveLength(TOTAL_ITEMS % SIZE);
    });

    it('writes the page it moves to, in the same number it shows', async () => {
      const user = userEvent.setup();
      mockPage(1);
      mockPage(3);
      const { router } = renderSection(summary());
      await screen.findByRole('table');

      await user.click(pagingButton(paging.last));

      expect(router.state.location.search).toBe('?page=3');
      await waitFor(() => {
        expect(nock.pendingMocks()).toEqual([]);
      });
    });

    it('is a step the browser’s back button walks', async () => {
      const user = userEvent.setup();
      mockPage(1).persist();
      mockPage(2);
      const { router } = renderSection(summary());
      await screen.findByRole('table');
      await user.click(pagingButton(paging.next));
      await waitFor(() => {
        expect(router.state.location.search).toBe('?page=2');
      });

      await router.navigate(-1);

      // A push rather than a replace: paging is a reader's own step, so going
      // back returns to the page they came from.
      await waitFor(() => {
        expect(router.state.location.search).toBe('');
      });
    });

    it('leaves the count, the page total and the ordering where they were', async () => {
      const user = userEvent.setup();
      mockPage(1, 'amount,desc');
      mockPage(2, 'amount,desc', 50);
      renderSection(summary(), `${SECTION_PATH}?sort=amount%2Cdesc`);
      await screen.findByRole('table');

      await user.click(pagingButton(paging.next));

      // Asserted *while the next page is in flight*, which is the half a check
      // on either side of the transition cannot see: the count and the total go
      // off screen entirely if the control unmounts to wait, and both
      // assertions would still pass.
      expect(statedCount()).toBeInTheDocument();
      expect(
        within(pagingControl()).getByText(paging.ofPages(formatCount(TOTAL_PAGES))),
      ).toBeInTheDocument();
      expect(sortChooser()).toHaveValue(copy.sort.amountDesc);

      await waitFor(() => {
        expect(shownSourceIds()).toEqual(pageOf(2).map((each) => String(each.sourceId)));
      });
      // Paging is a window onto the selection, not a change to it.
      expect(statedCount()).toBeInTheDocument();
      expect(
        within(pagingControl()).getByText(paging.ofPages(formatCount(TOTAL_PAGES))),
      ).toBeInTheDocument();
      expect(sortChooser()).toHaveValue(copy.sort.amountDesc);
    });

    it('keeps the page already read on screen while the next one is fetched', async () => {
      const user = userEvent.setup();
      mockPage(1);
      // Held open, so the in-flight state is a state this case owns rather than
      // a race it has to win against nock answering inside one macrotask.
      mockPage(2, undefined, 50);
      renderSection(summary());
      await screen.findByRole('table');
      const firstPage = shownSourceIds();

      await user.click(pagingButton(paging.next));

      // Still the rows just read, and said to be on their way out rather than
      // swapped for a spinner — which is also what leaves the button that was
      // pressed still there to hold the reader's focus.
      expect(shownSourceIds()).toEqual(firstPage);
      expect(contractsTable().closest('[aria-busy]')).toHaveAttribute('aria-busy', 'true');
      expect(document.activeElement).toBe(pagingButton(paging.next));

      await waitFor(() => {
        expect(shownSourceIds()).toEqual(pageOf(2).map((each) => String(each.sourceId)));
      });
      expect(contractsTable().closest('[aria-busy]')).toHaveAttribute('aria-busy', 'false');
    });

    it('says which page a reader is now on, the rows having changed under them', async () => {
      const user = userEvent.setup();
      mockPage(1);
      mockPage(2);
      renderSection(summary());
      await screen.findByRole('table');

      // The whole point of holding the control still is that focus stays put and
      // nothing is remounted — which is also what leaves a reader who cannot see
      // the table with no sign that anything happened, unless it is said.
      expect(announced()).toBe(copy.pageAnnounced('1', String(TOTAL_PAGES)));

      await user.click(pagingButton(paging.next));

      await waitFor(() => {
        expect(announced()).toBe(copy.pageAnnounced('2', String(TOTAL_PAGES)));
      });
    });
  });

  describe('a URL naming a page past the end', () => {
    /** What the API answers such a request with: empty, and carrying the truth. */
    function mockPastTheEnd(asked: number) {
      return mockContracts(
        { year: 2025, page: asked },
        200,
        page([], {
          page: asked,
          size: SIZE,
          totalItems: TOTAL_ITEMS,
          totalPages: TOTAL_PAGES,
        }),
      );
    }

    it('lands on the last page with the true count, not on an error or an empty table', async () => {
      mockPastTheEnd(9);
      mockPage(3);
      const { router } = renderSection(summary(), `${SECTION_PATH}?page=9`);

      await waitFor(() => {
        expect(router.state.location.search).toBe('?page=3');
      });
      expect(await screen.findByRole('table')).toBeInTheDocument();
      expect(shownSourceIds()).toEqual(pageOf(3).map((each) => String(each.sourceId)));
      expect(statedCount()).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('clamps in place, leaving nothing to go back to', async () => {
      mockPastTheEnd(9);
      mockPage(3).persist();
      const { router } = renderSection(summary(), `${SECTION_PATH}?page=9`);

      await waitFor(() => {
        expect(router.state.location.search).toBe('?page=3');
      });

      // Asserted on the history rather than by going back: a push would leave
      // `?page=9` behind, be walked back into, and be clamped again before an
      // assertion could read it — so the negative check would pass under the
      // very implementation it is written to reject.
      expect(router.state.historyAction).toBe('REPLACE');
    });
  });
});
