import { screen, waitFor } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  copy,
  mockAnyContracts,
  mockContracts,
  ORGANO_ID,
  ORGANO_NAME,
  page,
  renderSection,
  SECTION_PATH,
  summary,
  yearChooser,
  YEARS,
} from './sectionHarness';

async function openChooser(user: UserEvent) {
  await user.click(yearChooser());
}

async function chooseYear(user: UserEvent, year: string) {
  await openChooser(user);
  await user.click(screen.getByRole('option', { name: year }));
}

function offeredYears() {
  return screen.getAllByRole('option').map((option) => option.textContent);
}

function partialStatement() {
  return screen.queryByText(copy.partialBody);
}

function notUpdatedStatement() {
  return screen.queryByText(copy.notUpdatedBody);
}

describe('the contratos menores section', () => {
  beforeEach(() => {
    nock.disableNetConnect();
    // The list below the chooser reads on every render. These cases are about
    // the chooser and the two statements, so the read is answered without them
    // having to say for which year.
    mockAnyContracts();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
    vi.restoreAllMocks();
  });

  describe('the year chooser', () => {
    it('offers exactly the years the summary carries, newest first', async () => {
      const user = userEvent.setup();
      renderSection(summary());

      await openChooser(user);

      expect(offeredYears()).toEqual(['2025', '2024', '2023']);
    });

    it('offers nothing but years — no all-years, undated or placeholder entry', async () => {
      const user = userEvent.setup();
      renderSection(summary());

      await openChooser(user);

      // The count is the assertion: any extra affordance would be an option
      // beyond the three the Órgano has contracts in.
      expect(screen.getAllByRole('option')).toHaveLength(YEARS.length);
      expect(yearChooser()).not.toHaveAttribute('placeholder');
    });

    it('opens on the most recent year when the URL names none', () => {
      renderSection(summary());

      expect(yearChooser()).toHaveValue('2025');
    });

    it('opens on the year the URL already carries', () => {
      renderSection(summary(), `${SECTION_PATH}?year=2023`);

      expect(yearChooser()).toHaveValue('2023');
    });

    it('leaves the URL alone until a year is chosen', () => {
      const { router } = renderSection(summary());

      // The default is derived, not written: a reader who has chosen nothing
      // gets no entry in their history for a choice they did not make.
      expect(router.state.location.search).toBe('');
    });
  });

  describe('a URL naming a year the Órgano has no contracts in', () => {
    it('opens on the default rather than on an error', () => {
      renderSection(summary(), `${SECTION_PATH}?year=2019`);

      expect(yearChooser()).toHaveValue('2025');
    });

    it('opens on the default when the year is not a year at all', () => {
      renderSection(summary(), `${SECTION_PATH}?year=onte`);

      expect(yearChooser()).toHaveValue('2025');
    });

    it('corrects the URL to the year it opened on', async () => {
      // Displaying over a stale year would leave a reader holding a link that
      // says one thing while the section shows another — and the chooser cannot
      // resolve it for them, since picking the year already displayed is not a
      // change.
      const { router } = renderSection(summary(), `${SECTION_PATH}?year=2019`);

      await waitFor(() => {
        expect(router.state.location.search).toBe('?year=2025');
      });
    });

    it('corrects it in place, leaving nothing to go back to', async () => {
      const { router } = renderSection(summary(), `${SECTION_PATH}?year=2019`);
      await waitFor(() => {
        expect(router.state.location.search).toBe('?year=2025');
      });

      await router.navigate(-1);

      // A replace, not a push: the reader made no choice here, so the correction
      // is not a step in their history to be undone.
      expect(router.state.location.search).not.toBe('?year=2019');
    });

    it('respells a year written in a spelling the API does not take', async () => {
      const { router } = renderSection(summary(), `${SECTION_PATH}?year=02024`);

      await waitFor(() => {
        expect(router.state.location.search).toBe('?year=2024');
      });
      expect(yearChooser()).toHaveValue('2024');
    });

    it('keeps the rest of the query string while correcting it', async () => {
      const { router } = renderSection(summary(), `${SECTION_PATH}?sort=amount%2Cdesc&year=2019`);

      await waitFor(() => {
        const search = new URLSearchParams(router.state.location.search);
        expect(search.get('year')).toBe('2025');
        expect(search.get('sort')).toBe('amount,desc');
      });
    });
  });

  describe('choosing a year', () => {
    it('writes it to the query string as the API spells it', async () => {
      const user = userEvent.setup();
      const { router } = renderSection(summary());

      await chooseYear(user, '2024');

      expect(router.state.location.search).toBe('?year=2024');
      expect(yearChooser()).toHaveValue('2024');
    });

    it('is a step a reader can take back', async () => {
      const user = userEvent.setup();
      const { router } = renderSection(summary());

      await chooseYear(user, '2024');
      await router.navigate(-1);

      // A push, unlike the correction above: this one *was* a choice, so going
      // back returns to the year the section opened on.
      await waitFor(() => {
        expect(router.state.location.search).toBe('');
        expect(yearChooser()).toHaveValue('2025');
      });
    });

    it('keeps the ordering the URL already carries', async () => {
      // Scoped to `sort` on purpose. The paging task adds `page` and the rule
      // that changing the year returns to the first one, which this must not
      // read as a promise to preserve everything.
      const user = userEvent.setup();
      const { router } = renderSection(summary(), `${SECTION_PATH}?sort=amount%2Cdesc`);

      await chooseYear(user, '2023');

      const search = new URLSearchParams(router.state.location.search);
      expect(search.get('sort')).toBe('amount,desc');
      expect(search.get('year')).toBe('2023');
    });

    it('replaces the year already there rather than adding a second one', async () => {
      const user = userEvent.setup();
      const { router } = renderSection(summary(), `${SECTION_PATH}?year=2023`);

      await chooseYear(user, '2025');

      expect(router.state.location.search).toBe('?year=2025');
    });

    it('does nothing when the year already shown is picked again', async () => {
      const user = userEvent.setup();
      const { router } = renderSection(summary(), `${SECTION_PATH}?year=2024`);

      await chooseYear(user, '2024');

      // No deselect and no clear: re-picking cannot empty the chooser, and it
      // does not navigate either.
      expect(router.state.location.search).toBe('?year=2024');
      expect(yearChooser()).toHaveValue('2024');
    });
  });

  describe('what the section says about itself', () => {
    it('states that what is shown is incomplete while the import runs', () => {
      renderSection(summary({ partial: true }));

      expect(screen.getByText(copy.partialTitle)).toBeInTheDocument();
      expect(partialStatement()).toBeInTheDocument();
      expect(notUpdatedStatement()).not.toBeInTheDocument();
    });

    it('says nothing about being incomplete once the import has finished', () => {
      renderSection(summary({ partial: false }));

      expect(partialStatement()).not.toBeInTheDocument();
    });

    it('states that the Órgano is no longer updated, inertly rather than as an error', () => {
      renderSection(summary({ updating: false }));

      const statement = screen.getByText(copy.notUpdatedTitle).closest('[role="status"]');
      // Grey and polite: this is the reading a disabled account gets, not a
      // failure, so it neither shows red nor interrupts a screen reader.
      expect(statement).toHaveAttribute('data-variant', 'light');
      expect(statement).toHaveStyle({ '--alert-color': 'var(--mantine-color-gray-light-color)' });
      expect(notUpdatedStatement()).toBeInTheDocument();
      expect(partialStatement()).not.toBeInTheDocument();
    });

    it('says nothing about updates while the Órgano is still being refreshed', () => {
      renderSection(summary({ updating: true }));

      expect(notUpdatedStatement()).not.toBeInTheDocument();
    });

    it('states both at once for an Órgano unmarked halfway through its initial import', async () => {
      renderSection(summary({ partial: true, updating: false }));
      // Awaited because the list's own wait is a status too, and counting them
      // while it is on screen would count something that is not a statement.
      await screen.findByRole('table');

      // Two statements, not one combined status: neither replaces the other and
      // both are true.
      expect(partialStatement()).toBeInTheDocument();
      expect(notUpdatedStatement()).toBeInTheDocument();
      expect(screen.getAllByRole('status')).toHaveLength(2);
    });

    it('distinguishes the two, so neither is read as the other', () => {
      renderSection(summary({ partial: true, updating: false }));

      const partial = screen.getByText(copy.partialTitle).closest('[role="status"]');
      const notUpdated = screen.getByText(copy.notUpdatedTitle).closest('[role="status"]');
      expect(partial).not.toBe(notUpdated);
      // Each is a region of its own with its own name and body, so the two stay
      // distinguishable where colour says nothing.
      expect(partial).toHaveAccessibleName(copy.partialTitle);
      expect(notUpdated).toHaveAccessibleName(copy.notUpdatedTitle);
      expect(partial).toHaveStyle({ '--alert-color': 'var(--mantine-color-indigo-light-color)' });
      expect(notUpdated).toHaveStyle({ '--alert-color': 'var(--mantine-color-gray-light-color)' });
    });

    it('says neither when the Órgano is complete and still being refreshed', async () => {
      renderSection(summary());
      await screen.findByRole('table');

      expect(screen.queryAllByRole('status')).toEqual([]);
    });
  });

  describe('what it reads and what it does not', () => {
    it('asks for the contracts and for nothing else', async () => {
      // Every request is enumerated rather than inferred from the interceptors.
      // An unanswered one is not something a test would otherwise notice:
      // react-query catches it and, it being no `HttpError`, quietly retries —
      // so `pendingMocks` alone would say the contracts read happened without
      // saying it was the only one.
      nock.cleanAll();
      const requests = vi.spyOn(globalThis, 'fetch');
      mockContracts({ year: 2025 }, 200, page([]));

      renderSection(summary());

      expect(await screen.findByRole('table')).toBeInTheDocument();
      // The summary, the years and the Órgano's name arrive as context; a
      // section that read any of them would show up as a second entry here.
      // Every read in the app passes `apiFetch` a path, so this is the whole
      // shape a call can take here — a `Request` or a `URL` would be a read
      // written some other way, and is worth failing on rather than coercing.
      const asked = requests.mock.calls.map(([input]) => {
        expect(typeof input).toBe('string');
        return input as string;
      });
      // The whole selection, in the API's own spelling — the response states
      // neither the ordering nor which page it is, so a request naming only what
      // differs from the defaults would leave nothing anywhere saying what was
      // asked for.
      expect(asked).toEqual([
        `/api/organo/${ORGANO_ID}/contratos-menores?year=2025&sort=publicationDate%2Cdesc&page=1`,
      ]);
    });

    it('does not draw the Órgano’s name, which is the page’s above it', async () => {
      renderSection(summary());
      await screen.findByRole('table');

      expect(screen.queryByText(ORGANO_NAME)).not.toBeInTheDocument();
    });
  });
});
