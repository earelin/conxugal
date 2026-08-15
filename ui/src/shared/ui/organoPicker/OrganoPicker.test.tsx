import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { strings } from '../../lib/strings';
import { buildTaxonomiaView } from '../../lib/taxonomiaTree';
import {
  copy,
  cunqueiro,
  innovacion,
  openPicker,
  renderPicker,
  search,
  searchBox,
  TERMOS,
  trigger,
  vivenda,
} from './pickerHarness';

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
