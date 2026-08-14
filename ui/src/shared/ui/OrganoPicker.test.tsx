import { MantineProvider } from '@mantine/core';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
      // route tree, so the picker's selected state needs no /organo route —
      // which is just as well, since FEAT-0013 has not built one.
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
  const open = options.path === undefined ? undefined : openOrganoName(options.path);
  await user.click(trigger(open));
  return { ...utils, user };
}

function openOrganoName(path: string): string {
  const id = path.split('/')[2];
  return VIEW.catalogue.find((entry) => entry.id === id)?.name ?? copy.placeholder;
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

  it('marks the open Organo as selected and leaves its siblings alone', async () => {
    await openPicker({ path: `/organo/${cunqueiro.id}` });

    const tree = await screen.findByRole('tree', { name: copy.label });
    const selected = within(tree)
      .getAllByRole('treeitem')
      .filter((item) => item.getAttribute('aria-selected') === 'true');

    expect(selected).toHaveLength(1);
    expect(selected[0]).toHaveTextContent(cunqueiro.name);
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

  it('opens the focused Organo on Enter, which Mantine leaves unhandled', async () => {
    const { router, user } = await openPicker();

    const tree = await screen.findByRole('tree', { name: copy.label });
    const row = within(tree)
      .getAllByRole('treeitem')
      .find((item) => item.textContent === vivenda.name);
    row?.focus();
    await user.keyboard('{Enter}');

    await waitFor(() => expect(router.state.location.pathname).toBe(`/organo/${vivenda.id}`));
  });

  it('leaves the reader where they are when a term is chosen', async () => {
    const { router, onNavigate, user } = await openPicker();

    await user.click(await screen.findByText('Consellerías'));

    expect(router.state.location.pathname).toBe('/');
    expect(onNavigate).not.toHaveBeenCalled();
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
