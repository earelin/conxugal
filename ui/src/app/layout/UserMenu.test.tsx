import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CurrentUser } from '../../shared/entities/currentUser';
import { createQueryClient } from '../../shared/lib/queryClient';
import { strings } from '../../shared/lib/strings';
import { initialsOf } from '../../shared/ui/avatar';
import { theme } from '../theme';
import { UserMenu } from './UserMenu';

const BASE_URL = 'http://localhost:3000';

const adminUser: CurrentUser = {
  id: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  email: 'ana.pereira@conxugal.gal',
  role: 'ADMIN',
  createdAt: '2026-01-15T09:30:00Z',
  lastLoginAt: '2026-07-10T08:12:00Z',
};

function renderUserMenu() {
  return render(
    <MantineProvider theme={theme} env="test">
      <QueryClientProvider client={createQueryClient()}>
        <UserMenu currentUser={adminUser} />
      </QueryClientProvider>
    </MantineProvider>,
  );
}

function trigger() {
  return screen.getByRole('button', { name: strings.userMenu.trigger });
}

async function openMenu(user: ReturnType<typeof userEvent.setup>) {
  await user.click(trigger());
  return screen.findByRole('menuitem', { name: strings.userMenu.logout });
}

describe('UserMenu', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
    vi.restoreAllMocks();
  });

  it('renders a trigger with an accessible name, and keeps the dropdown out of the DOM while closed', () => {
    renderUserMenu();

    expect(trigger()).toBeInTheDocument();
    expect(screen.queryByText(strings.userMenu.logout)).not.toBeInTheDocument();
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('only hides the email/role text and chevron below sm, never the trigger or the avatar', () => {
    renderUserMenu();

    expect(screen.getByText(adminUser.email).closest('.mantine-visible-from-sm')).not.toBeNull();
    expect(trigger().className).not.toMatch(/mantine-visible-from-sm/);

    const initials = screen.getByText(initialsOf(adminUser.email));
    expect(initials.closest('.mantine-visible-from-sm')).toBeNull();

    const chevron = document.querySelector('.tabler-icon-chevron-down');
    expect(chevron?.closest('.mantine-visible-from-sm')).not.toBeNull();
  });

  it('opens the dropdown and shows the duplicated identity alongside the logout item', async () => {
    const user = userEvent.setup();
    renderUserMenu();

    await openMenu(user);

    const menu = screen.getByRole('menu');
    expect(
      within(menu).getByRole('menuitem', { name: strings.userMenu.logout }),
    ).toBeInTheDocument();
    expect(within(menu).getByText(adminUser.email)).toBeInTheDocument();
    expect(within(menu).getByText(strings.roleLabel.ADMIN)).toBeInTheDocument();

    expect(screen.getAllByText(adminUser.email)).toHaveLength(2);
  });

  it('navigates to /login once the logout call succeeds', async () => {
    const replace = vi.fn();
    vi.stubGlobal('location', { ...window.location, replace });
    nock(BASE_URL).post('/logout').reply(200);

    const user = userEvent.setup();
    renderUserMenu();
    await user.click(await openMenu(user));

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
  });

  it('marks the item non-interactive while the request is in flight, and a double click only fires one logout', async () => {
    let resolveFetch: (response: Response) => void;
    const pendingFetch = new Promise<Response>((resolve) => {
      resolveFetch = resolve;
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockReturnValue(pendingFetch);

    const user = userEvent.setup();
    renderUserMenu();
    const item = await openMenu(user);

    await user.dblClick(item);

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(item).toHaveAttribute('aria-disabled', 'true');

    resolveFetch!({ status: 200, ok: true } as Response);
    await vi.waitFor(() => expect(item).not.toHaveAttribute('aria-disabled'));
  });

  it('keeps the item focused (not natively disabled) while pending, so the keyboard path stays inside the menu', async () => {
    const replace = vi.fn();
    vi.stubGlobal('location', { ...window.location, replace });
    let resolveFetch: (response: Response) => void;
    const pendingFetch = new Promise<Response>((resolve) => {
      resolveFetch = resolve;
    });
    vi.spyOn(globalThis, 'fetch').mockReturnValue(pendingFetch);

    const user = userEvent.setup();
    renderUserMenu();

    trigger().focus();
    await user.keyboard('{Enter}');
    const item = await screen.findByRole('menuitem', { name: strings.userMenu.logout });

    await user.keyboard('{ArrowDown}');
    expect(item).toHaveFocus();

    await user.keyboard('{Enter}');
    await vi.waitFor(() => expect(item).toHaveAttribute('aria-disabled', 'true'));
    expect(item).toHaveFocus();

    resolveFetch!({ status: 200, ok: true } as Response);
    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
  });

  it('keeps the dropdown open and shows the Galician failure message on a non-401 failure', async () => {
    const replace = vi.fn();
    vi.stubGlobal('location', { ...window.location, replace });
    nock(BASE_URL).post('/logout').reply(500);

    const user = userEvent.setup();
    renderUserMenu();
    await user.click(await openMenu(user));

    expect(await screen.findByText(strings.userMenu.logoutError)).toBeInTheDocument();
    expect(screen.getByRole('menu')).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it('does not show the failure message for a 401, which the shared session-loss handler owns instead', async () => {
    const replace = vi.fn();
    vi.stubGlobal('location', { ...window.location, replace });
    nock(BASE_URL).post('/logout').reply(401);

    const user = userEvent.setup();
    renderUserMenu();
    await user.click(await openMenu(user));

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
    expect(screen.queryByText(strings.userMenu.logoutError)).not.toBeInTheDocument();
  });

  it('clears a previous failure once the dropdown is closed and reopened', async () => {
    nock(BASE_URL).post('/logout').reply(500);

    const user = userEvent.setup();
    renderUserMenu();
    await user.click(await openMenu(user));

    expect(await screen.findByText(strings.userMenu.logoutError)).toBeInTheDocument();

    await user.keyboard('{Escape}');
    await vi.waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());

    await openMenu(user);

    expect(screen.queryByText(strings.userMenu.logoutError)).not.toBeInTheDocument();
  });
});
