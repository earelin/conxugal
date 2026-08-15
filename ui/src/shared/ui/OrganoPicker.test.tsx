import { MantineProvider } from '@mantine/core';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { createMemoryRouter, type RouteObject } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { describe, expect, it, vi } from 'vitest';

import { strings } from '../lib/strings';
import { buildTaxonomiaView, type Organo, type Termo } from '../lib/taxonomiaTree';
import { OrganoPicker } from './OrganoPicker';

const copy = strings.organoPicker;

function termo(id: string, name: string, parentId: string | null = null): Termo {
  return { id, name, parentId };
}

function organo(id: string, name: string, termoId: string | null, active = true): Organo {
  return { id, name, active, termoId };
}

const TERMOS = [
  termo('t-1', 'Consellerías'),
  termo('t-2', 'Consellería de Educación', 't-1'),
  termo('t-3', 'Axencias e entidades instrumentais', 't-2'),
  termo('t-4', 'Consellería de Sanidade', 't-1'),
  termo('t-5', 'Concellos'),
];

const innovacion = organo('o-1', 'Axencia Galega de Innovación', 't-3');
const cunqueiro = organo('o-2', 'Hospital Álvaro Cunqueiro', 't-4', false);
const vivenda = organo('o-3', 'Instituto Galego da Vivenda e Solo', null);

const VIEW = buildTaxonomiaView(TERMOS, [innovacion, cunqueiro, vivenda]);

interface RenderOptions {
  view?: typeof VIEW | null;
  isPending?: boolean;
  isError?: boolean;
  onRetry?: () => void;
  onNavigate?: () => void;
  path?: string;
}

function renderPicker({
  view = VIEW,
  isPending = false,
  isError = false,
  onRetry = vi.fn(),
  onNavigate = vi.fn(),
  path = '/',
}: RenderOptions = {}) {
  const routes: RouteObject[] = [
    {
      // A splat matches every path: `useMatch` reads the location, not the
      // route tree, so the picker needs no /organo route to resolve what is
      // open — which is just as well, since none exists yet.
      path: '*',
      element: (
        <OrganoPicker
          view={view}
          isPending={isPending}
          isFetching={false}
          isError={isError}
          onRetry={onRetry}
          onNavigate={onNavigate}
        />
      ),
    },
  ];
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    // `env="test"` turns off Mantine's transitions, without which the popover
    // spends its first frames `display: none` and hides its own contents from
    // every accessible query.
    <MantineProvider env="test">
      <RouterProvider router={router} />
    </MantineProvider>,
  );
  return { router, onRetry, onNavigate };
}

function trigger(name: string = copy.placeholder) {
  return screen.getByRole('button', { name: `${copy.label} ${name}` });
}

/** Renders the picker and drops it down, which is where everything below is. */
async function openPicker(options: RenderOptions = {}) {
  const utils = renderPicker(options);
  const user = userEvent.setup();
  // The closed control holds one button, and its accessible name depends on
  // which Órgano is open — so reach it by role rather than restating the name.
  await user.click(screen.getByRole('button'));
  return { ...utils, user };
}

function searchBox() {
  return screen.getByRole('textbox', { name: copy.searchLabel });
}

/** Types into the filter, waiting for the dropdown to have drawn it first. */
async function search(user: UserEvent, query: string) {
  await screen.findByRole('textbox', { name: copy.searchLabel });
  await user.clear(searchBox());
  await user.type(searchBox(), query);
}

/** The matches the filter offers, which share the tree's accessible name. */
function matchList() {
  return screen.getByRole('listbox', { name: copy.label });
}

/** A tree row by its own label, ignoring the labels of its descendants. */
function treeRow(tree: HTMLElement, label: string): HTMLElement {
  // A row's own label is its first child; its descendants live in the nested
  // list after it, so `textContent` on the row itself would match a parent.
  const row = within(tree)
    .getAllByRole('treeitem')
    .find((item) => item.firstElementChild?.textContent === label);
  if (row === undefined) {
    throw new Error(`No tree row labelled ${label}`);
  }
  return row;
}

describe('OrganoPicker closed', () => {
  it('names the label and the placeholder while no Organo is open', () => {
    renderPicker();

    expect(screen.getByText(copy.label)).toBeInTheDocument();
    expect(trigger()).toBeInTheDocument();
    expect(screen.queryByRole('tree')).not.toBeInTheDocument();
  });

  it('names the open Organo when the reader is on its page', () => {
    renderPicker({ path: `/organo/${cunqueiro.id}/contratos-menores` });

    expect(trigger(cunqueiro.name)).toBeInTheDocument();
  });
});

describe('OrganoPicker tree', () => {
  it('nests each Organo under the term it is filed in', async () => {
    await openPicker();

    const tree = await screen.findByRole('tree', { name: copy.label });
    expect(within(tree).getByText('Consellerías')).toBeInTheDocument();
    expect(within(tree).getByText('Axencias e entidades instrumentais')).toBeInTheDocument();
    expect(within(tree).getByText(innovacion.name)).toBeInTheDocument();
    expect(within(tree).getByText(cunqueiro.name)).toBeInTheDocument();
  });

  it('shows an unclassified Organo at the root, after the root terms', async () => {
    await openPicker();

    const tree = await screen.findByRole('tree', { name: copy.label });
    const roots = within(tree)
      .getAllByRole('treeitem')
      .filter((item) => item.dataset.level === '1');

    expect(roots.map((item) => item.dataset.value)).toEqual([`termo:t-1`, `organo:${vivenda.id}`]);
  });

  it('omits a term whose whole subtree holds no Organo, keeping one whose descendant has', async () => {
    await openPicker();

    const tree = await screen.findByRole('tree', { name: copy.label });
    // Concellos holds nothing at any level; Consellería de Educación holds
    // nothing of its own but has a descendant that does.
    expect(within(tree).queryByText('Concellos')).not.toBeInTheDocument();
    expect(within(tree).getByText('Consellería de Educación')).toBeInTheDocument();
  });

  it('leaves the indent Mantine draws from the level alone on every row', async () => {
    await openPicker();

    const tree = await screen.findByRole('tree', { name: copy.label });
    const rows = within(tree)
      .getAllByRole('treeitem')
      .map((item) => item.firstElementChild as HTMLElement);

    // The indent is a stylesheet rule keyed on the level Mantine sets per row,
    // and the setup file never loads Mantine's CSS — so there is no rule here
    // to measure, only the two ways of breaking it to assert against.
    for (const row of rows) {
      // Mantine hands the rule's class to the row through `elementProps`, and
      // `data-value` arrives on the same spread. Dropping it would cost the
      // indent and the keyboard focus ring, which the rule also carries.
      expect(row).toHaveAttribute('data-value');
      // Any padding prop covering the start side overrides that rule inline
      // and flattens the tree: `px` writes `padding-inline`, `ps` writes
      // `padding-inline-start`, and `p`/`pl` write neither but still win.
      expect(row.style.paddingInline).toBe('');
      expect(row.style.paddingInlineStart).toBe('');
      expect(row.style.padding).toBe('');
      expect(row.style.paddingLeft).toBe('');
    }
  });

  it('marks the open Organo as selected and leaves its siblings alone', async () => {
    await openPicker({ path: `/organo/${cunqueiro.id}` });

    const tree = await screen.findByRole('tree', { name: copy.label });
    const selected = within(tree)
      .getAllByRole('treeitem')
      .filter((item) => item.getAttribute('aria-selected') === 'true');

    expect(selected).toHaveLength(1);
    expect(selected[0]).toHaveTextContent(cunqueiro.name);
  });

  it('paints the open Organo in the variant that is defined for both colour schemes', async () => {
    await openPicker({ path: `/organo/${cunqueiro.id}` });

    const tree = await screen.findByRole('tree', { name: copy.label });
    const row = treeRow(tree, cunqueiro.name).firstElementChild as HTMLElement;

    // A step off the indigo scale is one fixed colour and reads against one
    // scheme only; the `light` variant pair is redefined per scheme.
    expect(row.style.background).toBe('var(--mantine-color-indigo-light)');
    expect(row.style.color).toBe('var(--mantine-color-indigo-light-color)');
  });

  it('offers no control that creates, renames, moves, deletes or reassigns anything', async () => {
    await openPicker();

    const tree = await screen.findByRole('tree', { name: copy.label });
    expect(within(tree).queryAllByRole('button')).toHaveLength(0);
    expect(within(tree).queryAllByRole('link')).toHaveLength(0);
  });
});

describe('OrganoPicker selection', () => {
  it('opens the chosen Organo, closes the dropdown and closes the navbar', async () => {
    const { router, onNavigate, user } = await openPicker();

    await user.click(await screen.findByText(innovacion.name));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/organo/${innovacion.id}`));
    expect(onNavigate).toHaveBeenCalledOnce();
    await waitFor(() => expect(screen.queryByRole('tree')).not.toBeInTheDocument());
  });

  it('leaves the reader where they are when a term is chosen', async () => {
    const { router, onNavigate, user } = await openPicker();

    await user.click(await screen.findByText('Consellerías'));

    expect(router.state.location.pathname).toBe('/');
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it('stays where it is when the Organo already open is chosen again', async () => {
    const { router, user } = await openPicker({ path: `/organo/${cunqueiro.id}` });

    const tree = await screen.findByRole('tree', { name: copy.label });
    await user.click(within(treeRow(tree, cunqueiro.name)).getByText(cunqueiro.name));

    // Re-choosing what is already open closes the dropdown without stacking a
    // second copy of the same page onto the history.
    expect(router.state.location.pathname).toBe(`/organo/${cunqueiro.id}`);
    expect(router.state.historyAction).toBe('POP');
    await waitFor(() => expect(screen.queryByRole('tree')).not.toBeInTheDocument());
  });
});

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

  it('keeps the branches the reader collapsed while they check a name', async () => {
    const { user } = await openPicker();

    const tree = await screen.findByRole('tree', { name: copy.label });
    treeRow(tree, 'Consellería de Sanidade').focus();
    await user.keyboard('{Enter}');
    expect(within(tree).queryByText(cunqueiro.name)).not.toBeInTheDocument();

    await search(user, 'galega');
    await user.clear(searchBox());

    // The same tree, still collapsed — not a fresh one with every branch open.
    expect(
      within(screen.getByRole('tree', { name: copy.label })).queryByText(cunqueiro.name),
    ).not.toBeInTheDocument();
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

describe('OrganoPicker keyboard', () => {
  it('moves focus into the filter when it opens, and back to the trigger on Escape', async () => {
    const { user } = await openPicker();

    // The filter is the first thing in the dropdown, so a reader who opened the
    // control to find a name types straight away; the tree is one Tab further.
    await waitFor(() => expect(document.activeElement).toBe(searchBox()));
    expect(screen.getByRole('tree', { name: copy.label })).toBeInTheDocument();

    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('tree')).not.toBeInTheDocument());
    expect(document.activeElement).toBe(trigger());
  });

  it('opens the focused Organo on Enter, which Mantine leaves unhandled', async () => {
    const { router, user } = await openPicker();

    treeRow(await screen.findByRole('tree', { name: copy.label }), vivenda.name).focus();
    await user.keyboard('{Enter}');

    await waitFor(() => expect(router.state.location.pathname).toBe(`/organo/${vivenda.id}`));
  });

  it('collapses and reopens a term on Enter rather than doing nothing', async () => {
    const { router, user } = await openPicker();

    const tree = await screen.findByRole('tree', { name: copy.label });
    treeRow(tree, 'Consellería de Sanidade').focus();
    await user.keyboard('{Enter}');

    await waitFor(() => expect(within(tree).queryByText(cunqueiro.name)).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe('/');

    await user.keyboard('{Enter}');
    expect(within(tree).getByText(cunqueiro.name)).toBeInTheDocument();
  });

  it('never opens an Organo the reader only ranged over with Shift', async () => {
    const { router, onNavigate, user } = await openPicker();

    // Clicking a term row is what sets Mantine's range anchor, and its
    // keyboard handler then applies a range whether or not
    // `allowRangeSelection` is set. The range must not read as a choice: it
    // heads with whichever Órgano happens to sit above the anchor.
    const tree = await screen.findByRole('tree', { name: copy.label });
    const sanidade = treeRow(tree, 'Consellería de Sanidade');
    await user.click(within(sanidade).getByText('Consellería de Sanidade'));
    sanidade.focus();
    await user.keyboard('{Shift>}{ArrowUp}{/Shift}');

    expect(router.state.location.pathname).toBe('/');
    expect(onNavigate).not.toHaveBeenCalled();
    expect(screen.getByRole('tree', { name: copy.label })).toBeInTheDocument();
  });
});

describe('OrganoPicker states', () => {
  it('says it is loading, with nothing to retry', async () => {
    await openPicker({ view: null, isPending: true });

    expect(await screen.findByText(strings.loading)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: strings.retry })).not.toBeInTheDocument();
    expect(screen.queryByText(copy.empty)).not.toBeInTheDocument();
  });

  it('says an empty visible set is empty, and offers no retry for it', async () => {
    await openPicker({ view: buildTaxonomiaView(TERMOS, []) });

    expect(await screen.findByText(copy.empty)).toBeInTheDocument();
    expect(screen.getByText(copy.emptyHelp)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: strings.retry })).not.toBeInTheDocument();
    expect(screen.queryByText(copy.errorTitle)).not.toBeInTheDocument();
    expect(screen.queryByRole('tree')).not.toBeInTheDocument();
  });

  it('reports a failed read as a failure, with a retry that re-reads', async () => {
    const { onRetry, user } = await openPicker({ view: null, isError: true });

    const retry = await screen.findByRole('button', { name: strings.retry });
    expect(screen.getByText(copy.errorTitle)).toBeInTheDocument();
    expect(screen.getByText(copy.errorHelp)).toBeInTheDocument();
    expect(screen.queryByText(copy.empty)).not.toBeInTheDocument();
    expect(screen.queryByRole('tree')).not.toBeInTheDocument();

    await user.click(retry);
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
