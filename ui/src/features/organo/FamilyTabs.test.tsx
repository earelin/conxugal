import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { bar, contratosMenores, licitacions, ORGANO_ID, renderTabs, tabs } from './organoHarness';

describe('FamilyTabs', () => {
  it('draws the full bar for a single family, which is not a defect', () => {
    renderTabs([contratosMenores], contratosMenores);

    expect(bar()).toBeInTheDocument();
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
    // `type` is what a Mantine tab carries as a button; on an anchor it would
    // claim the linked document's media type.
    expect(tabs().every((tab) => !tab.hasAttribute('type'))).toBe(true);
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

  it('chooses on Space too, which a role=tab is expected to answer', async () => {
    const user = userEvent.setup();
    const { router } = renderTabs([contratosMenores, licitacions], contratosMenores);

    await user.tab();
    await user.keyboard('{ArrowRight}');
    await user.keyboard(' ');

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
