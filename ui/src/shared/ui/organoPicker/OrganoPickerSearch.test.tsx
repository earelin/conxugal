import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import {
  copy,
  cunqueiro,
  innovacion,
  matchList,
  openPicker,
  search,
  searchBox,
  trigger,
  vivenda,
} from './pickerHarness';

describe('OrganoPicker search', () => {
  it('offers the matching Organos as the reader types, with nothing to submit', async () => {
    const { user } = await openPicker();

    await search(user, 'galega');

    expect(within(matchList()).getByText(innovacion.name)).toBeInTheDocument();
    expect(within(matchList()).queryByText(cunqueiro.name)).not.toBeInTheDocument();
    expect(screen.queryByRole('tree')).not.toBeInTheDocument();
  });

  it('finds a name differing only by accent or case, and one by a fragment inside it', async () => {
    const { user } = await openPicker();

    await search(user, 'ALVARO');
    expect(within(matchList()).getByText(cunqueiro.name)).toBeInTheDocument();

    await search(user, 'galego da');
    expect(within(matchList()).getByText(vivenda.name)).toBeInTheDocument();
  });

  it('states that an offered Organo is inactive, and says nothing of an active one', async () => {
    const { user } = await openPicker();

    // Every fixture holds an «a», so all three are offered at once.
    await search(user, 'a');

    expect(within(matchList()).getAllByRole('option')).toHaveLength(3);
    expect(
      within(matchList()).getByRole('option', { name: `${cunqueiro.name} ${copy.inactive}` }),
    ).toBeInTheDocument();
    expect(within(matchList()).getByRole('option', { name: vivenda.name })).toBeInTheDocument();
    expect(within(matchList()).getAllByText(copy.inactive)).toHaveLength(1);
  });

  it('shows the tree for a whitespace-only query, which has asked nothing', async () => {
    const { user } = await openPicker();

    await search(user, '   ');

    expect(screen.getByRole('tree', { name: copy.label })).toBeInTheDocument();
    expect(screen.queryByRole('listbox', { name: copy.label })).not.toBeInTheDocument();
    expect(screen.queryByText(copy.noMatchesHelp)).not.toBeInTheDocument();
  });

  it('says a query matched nothing, quoting it, rather than listing the catalogue', async () => {
    const { user } = await openPicker();

    await search(user, 'sanidde');

    expect(screen.getByText(copy.noMatches('sanidde'))).toBeInTheDocument();
    expect(screen.getByText(copy.noMatchesHelp)).toBeInTheDocument();
    expect(screen.queryByRole('tree')).not.toBeInTheDocument();
    expect(screen.queryByRole('listbox', { name: copy.label })).not.toBeInTheDocument();
  });

  it('shows the tree again once the filter is emptied', async () => {
    const { user } = await openPicker();

    await search(user, 'galega');
    await user.clear(searchBox());

    expect(screen.getByRole('tree', { name: copy.label })).toBeInTheDocument();
    expect(screen.queryByRole('listbox', { name: copy.label })).not.toBeInTheDocument();
  });

  it('opens a chosen match exactly as choosing it in the tree does', async () => {
    const { router, onNavigate, user } = await openPicker();

    await search(user, 'alvaro');
    await user.click(within(matchList()).getByText(cunqueiro.name));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/organo/${cunqueiro.id}`));
    expect(onNavigate).toHaveBeenCalledOnce();
    await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument());
  });

  it('reopens on the tree rather than on a query the reader has forgotten typing', async () => {
    const { user } = await openPicker();

    await search(user, 'galega');
    await user.keyboard('{Escape}');
    await user.click(trigger());

    expect(await screen.findByRole('tree', { name: copy.label })).toBeInTheDocument();
    expect(searchBox()).toHaveValue('');
  });

  it('announces the refusal from a region that was already there to hear it', async () => {
    const { user } = await openPicker();

    // A region mounted along with its own message announces nothing: it has to
    // outlive the query that empties it.
    const status = screen.getByRole('status');
    expect(status).toBeEmptyDOMElement();

    await search(user, 'sanidde');

    expect(within(status).getByText(copy.noMatches('sanidde'))).toBeInTheDocument();
  });

  it('names the list the filter narrows, so the two are tied together', async () => {
    const { user } = await openPicker();

    await search(user, 'galega');

    expect(searchBox()).toHaveAttribute('aria-controls', matchList().id);
  });

  it('points at no list while there is none to point at', async () => {
    const { user } = await openPicker();

    // The tree is showing, so the listbox the attribute would name is hidden.
    expect(searchBox()).not.toHaveAttribute('aria-controls');

    await search(user, 'sanidde');

    expect(searchBox()).not.toHaveAttribute('aria-controls');
  });

  it('marks the open Organo as selected among the matches, as the tree does', async () => {
    const { user } = await openPicker({ path: `/organo/${cunqueiro.id}` });

    await search(user, 'a');

    const selected = within(matchList()).getAllByRole('option', { selected: true });
    expect(selected).toHaveLength(1);
    expect(selected[0]).toHaveTextContent(cunqueiro.name);
  });

  it('puts the one tab stop on the open Organo rather than on the first match', async () => {
    const { user } = await openPicker({ path: `/organo/${cunqueiro.id}` });

    await search(user, 'a');

    // Name order puts Cunqueiro second, so a tab stop pinned to the first row
    // would read as this passing while landing on the wrong Órgano.
    const options = within(matchList()).getAllByRole('option');
    expect(options.map((option) => option.textContent)).toEqual([
      innovacion.name,
      `${cunqueiro.name}${copy.inactive}`,
      vivenda.name,
    ]);
    expect(options.map((option) => option.tabIndex)).toEqual([-1, 0, -1]);
  });
});

describe('OrganoPicker search keyboard', () => {
  it('walks the matches from the filter box with the arrows alone', async () => {
    const { user } = await openPicker();

    await search(user, 'a');
    await user.keyboard('{ArrowDown}');

    const options = within(matchList()).getAllByRole('option');
    expect(document.activeElement).toBe(options[0]);

    await user.keyboard('{ArrowDown}');
    expect(document.activeElement).toBe(options[1]);

    await user.keyboard('{End}');
    expect(document.activeElement).toBe(options[2]);

    await user.keyboard('{Home}');
    expect(document.activeElement).toBe(options[0]);
  });

  it('holds the whole list to one tab stop, so the filter stays a Tab away', async () => {
    const { user } = await openPicker();

    await search(user, 'a');

    const options = within(matchList()).getAllByRole('option');
    expect(options.map((option) => option.tabIndex)).toEqual([0, -1, -1]);
  });

  it('opens the focused match on Enter, as a button does', async () => {
    const { router, user } = await openPicker();

    await search(user, 'alvaro');
    await user.keyboard('{ArrowDown}{Enter}');

    await waitFor(() => expect(router.state.location.pathname).toBe(`/organo/${cunqueiro.id}`));
  });
});
