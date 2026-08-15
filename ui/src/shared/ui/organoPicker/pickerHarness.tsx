import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { createMemoryRouter, type RouteObject } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { vi } from 'vitest';

import { strings } from '../../lib/strings';
import { buildTaxonomiaView, type Organo, type Termo } from '../../lib/taxonomiaTree';
import { OrganoPicker } from './OrganoPicker';

/**
 * The fixtures and the way in, shared by the picker's two test files. It holds
 * no assertion: what a state should say belongs beside the case asserting it.
 */
export const copy = strings.organoPicker;

function termo(id: string, name: string, parentId: string | null = null): Termo {
  return { id, name, parentId };
}

function organo(id: string, name: string, termoId: string | null, active = true): Organo {
  return { id, name, active, termoId };
}

export const TERMOS = [
  termo('t-1', 'Consellerías'),
  termo('t-2', 'Consellería de Educación', 't-1'),
  termo('t-3', 'Axencias e entidades instrumentais', 't-2'),
  termo('t-4', 'Consellería de Sanidade', 't-1'),
  termo('t-5', 'Concellos'),
];

export const innovacion = organo('o-1', 'Axencia Galega de Innovación', 't-3');
export const cunqueiro = organo('o-2', 'Hospital Álvaro Cunqueiro', 't-4', false);
export const vivenda = organo('o-3', 'Instituto Galego da Vivenda e Solo', null);

export const VIEW = buildTaxonomiaView(TERMOS, [innovacion, cunqueiro, vivenda]);

export interface RenderOptions {
  view?: typeof VIEW | null;
  isPending?: boolean;
  isError?: boolean;
  onRetry?: () => void;
  onNavigate?: () => void;
  path?: string;
}

export function renderPicker({
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

export function trigger(name: string = copy.placeholder) {
  return screen.getByRole('button', { name: `${copy.label} ${name}` });
}

/** Renders the picker and drops it down, which is where everything below is. */
export async function openPicker(options: RenderOptions = {}) {
  const utils = renderPicker(options);
  const user = userEvent.setup();
  // The closed control holds one button, and its accessible name depends on
  // which Órgano is open — so reach it by role rather than restating the name.
  await user.click(screen.getByRole('button'));
  return { ...utils, user };
}

export function searchBox() {
  return screen.getByRole('textbox', { name: copy.searchLabel });
}

/** Types into the filter, waiting for the dropdown to have drawn it first. */
export async function search(user: UserEvent, query: string) {
  const box = await screen.findByRole('textbox', { name: copy.searchLabel });
  await user.clear(box);
  await user.type(box, query);
}

/** The matches the filter offers, which share the tree's accessible name. */
export function matchList() {
  return screen.getByRole('listbox', { name: copy.label });
}

export function pickerTree() {
  return screen.findByRole('tree', { name: copy.label });
}
