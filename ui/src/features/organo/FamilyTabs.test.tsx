import { MantineProvider } from '@mantine/core';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { describe, expect, it } from 'vitest';

import { theme } from '../../app/theme';
import { FAMILIES, type Family } from './families';
import { FamilyTabs } from './FamilyTabs';

const ORGANO_ID = 'o-1';
const contratosMenores = FAMILIES[0];
// Stands in for the next family to be built, which is what the bar exists from
// the first day to make room for.
const licitacions: Family = { key: 'licitacions', path: 'licitacions', label: 'Licitacións' };

function renderTabs(held: Family[], active: Family) {
  const router = createMemoryRouter(
    [
      {
        path: '/organo/:id/:family',
        element: (
          <FamilyTabs organoId={ORGANO_ID} held={held} active={active}>
            <p>{'A sección da familia activa'}</p>
          </FamilyTabs>
        ),
      },
    ],
    { initialEntries: [`/organo/${ORGANO_ID}/${active.path}`] },
  );
  const utils = render(
    <MantineProvider theme={theme} env="test">
      <RouterProvider router={router} />
    </MantineProvider>,
  );
  return { ...utils, router };
}

function tabs() {
  return within(screen.getByRole('tablist')).getAllByRole('tab');
}

describe('FamilyTabs', () => {
  it('draws the full bar for a single family, which is not a defect', () => {
    renderTabs([contratosMenores], contratosMenores);

    expect(screen.getByRole('tablist')).toBeInTheDocument();
    expect(tabs()).toHaveLength(1);
    expect(tabs()[0]).toHaveAccessibleName(contratosMenores.label);
  });

  it('draws every family it is given, in the order it is given them', () => {
    renderTabs([contratosMenores, licitacions], contratosMenores);

    expect(tabs().map((tab) => tab.textContent)).toEqual([
      contratosMenores.label,
      licitacions.label,
    ]);
  });

  it('marks the family the URL names as the selected one', () => {
    renderTabs([contratosMenores, licitacions], licitacions);

    expect(screen.getByRole('tab', { name: licitacions.label })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByRole('tab', { name: contratosMenores.label })).toHaveAttribute(
      'aria-selected',
      'false',
    );
  });

  it('opens a family by its own URL, so the back button and a deep link agree', async () => {
    const user = userEvent.setup();
    const { router } = renderTabs([contratosMenores, licitacions], contratosMenores);

    await user.click(screen.getByRole('tab', { name: licitacions.label }));

    expect(router.state.location.pathname).toBe(`/organo/${ORGANO_ID}/${licitacions.path}`);
  });

  it('offers each family as a link, so a tab can be opened in a window of its own', () => {
    renderTabs([contratosMenores, licitacions], contratosMenores);

    expect(tabs().map((tab) => tab.getAttribute('href'))).toEqual([
      `/organo/${ORGANO_ID}/${contratosMenores.path}`,
      `/organo/${ORGANO_ID}/${licitacions.path}`,
    ]);
  });

  it('moves focus along the bar without navigating, so arrowing past a tab is not a visit', async () => {
    const user = userEvent.setup();
    const { router } = renderTabs([contratosMenores, licitacions], contratosMenores);

    await user.tab();
    await user.keyboard('{ArrowRight}');

    expect(screen.getByRole('tab', { name: licitacions.label })).toHaveFocus();
    expect(router.state.location.pathname).toBe(`/organo/${ORGANO_ID}/${contratosMenores.path}`);

    await user.keyboard('{Enter}');

    expect(router.state.location.pathname).toBe(`/organo/${ORGANO_ID}/${licitacions.path}`);
  });

  it('renders the active family section in the panel the selected tab points at', () => {
    renderTabs([contratosMenores, licitacions], licitacions);

    const panel = screen.getByRole('tabpanel');
    expect(within(panel).getByText('A sección da familia activa')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: licitacions.label })).toHaveAttribute(
      'aria-controls',
      panel.id,
    );
  });
});
