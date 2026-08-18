import { screen } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import {
  chosenYearShown,
  copy,
  ORGANO_NAME,
  renderSection,
  SECTION_PATH,
  summary,
  yearChooser,
  YEARS,
} from './sectionHarness';

async function openChooser(user: UserEvent) {
  await user.click(yearChooser());
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

      expect(chosenYearShown()).toBe('2025');
    });

    it('opens on the year the URL already carries', () => {
      renderSection(summary(), `${SECTION_PATH}?year=2023`);

      expect(chosenYearShown()).toBe('2023');
    });

    it('opens on the default for a year the Órgano has no contracts in', () => {
      // A link that has outlived the selection it named is not an error and not
      // an empty list.
      renderSection(summary(), `${SECTION_PATH}?year=2019`);

      expect(chosenYearShown()).toBe('2025');
    });

    it('opens on the default for a year that is not a year at all', () => {
      renderSection(summary(), `${SECTION_PATH}?year=onte`);

      expect(chosenYearShown()).toBe('2025');
    });

    it('leaves the URL alone until a year is chosen', () => {
      const { router } = renderSection(summary());

      // The default is derived, not written: a reader who has chosen nothing
      // gets no entry in their history for a choice they did not make.
      expect(router.state.location.search).toBe('');
    });
  });

  describe('choosing a year', () => {
    it('writes it to the query string as the API spells it', async () => {
      const user = userEvent.setup();
      const { router } = renderSection(summary());

      await openChooser(user);
      await user.click(screen.getByRole('option', { name: '2024' }));

      expect(router.state.location.search).toBe('?year=2024');
      expect(chosenYearShown()).toBe('2024');
    });

    it('keeps whatever else was riding in the query string', async () => {
      const user = userEvent.setup();
      const { router } = renderSection(summary(), `${SECTION_PATH}?sort=amount%2Cdesc`);

      await openChooser(user);
      await user.click(screen.getByRole('option', { name: '2023' }));

      expect(new URLSearchParams(router.state.location.search).get('sort')).toBe('amount,desc');
      expect(new URLSearchParams(router.state.location.search).get('year')).toBe('2023');
    });

    it('replaces the year already there rather than adding a second one', async () => {
      const user = userEvent.setup();
      const { router } = renderSection(summary(), `${SECTION_PATH}?year=2023`);

      await openChooser(user);
      await user.click(screen.getByRole('option', { name: '2025' }));

      expect(router.state.location.search).toBe('?year=2025');
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

    it('states both at once for an Órgano unmarked halfway through its initial import', () => {
      renderSection(summary({ partial: true, updating: false }));

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
      expect(partial).toHaveStyle({ '--alert-color': 'var(--mantine-color-indigo-light-color)' });
      expect(notUpdated).toHaveStyle({ '--alert-color': 'var(--mantine-color-gray-light-color)' });
    });

    it('says neither when the Órgano is complete and still being refreshed', () => {
      renderSection(summary());

      expect(screen.queryAllByRole('status')).toEqual([]);
    });
  });

  describe('what it does not read', () => {
    it('renders from the supplied context alone, with no query client behind it', () => {
      // The harness mounts no `QueryClientProvider`: a section that asked for
      // the summary, the years or the Órgano's name would throw rather than
      // quietly repeat a request the page has already made.
      renderSection(summary());

      expect(yearChooser()).toBeInTheDocument();
    });

    it('does not draw the Órgano’s name, which is the page’s above it', () => {
      renderSection(summary());

      expect(screen.queryByText(ORGANO_NAME)).not.toBeInTheDocument();
    });
  });
});
