import { MantineProvider } from '@mantine/core';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createQueryClient } from '../../api/queryClient';
import type { UserAccount } from '../../api/users';
import { strings } from '../../strings';
import { theme } from '../../theme';
import { UsersPage } from './UsersPage';

const BASE_URL = 'http://localhost:3000';

const adminUser: UserAccount = {
  id: 'admin-1',
  email: 'ana.pereira@conxugal.gal',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2025-02-03T09:00:00Z',
  lastLoginAt: '2026-07-10T08:12:00Z',
};

const neverLoggedInUser: UserAccount = {
  id: 'user-2',
  email: 'diego.senra@conxugal.gal',
  role: 'USER',
  enabled: true,
  createdAt: '2026-05-05T09:00:00Z',
  lastLoginAt: null,
};

const disabledUser: UserAccount = {
  id: 'user-3',
  email: 'helena.mar@conxugal.gal',
  role: 'USER',
  enabled: false,
  createdAt: '2025-09-18T09:00:00Z',
  lastLoginAt: '2026-03-02T09:00:00Z',
};

function mockUsersList(users: UserAccount[]) {
  return nock(BASE_URL).get('/api/admin/users').reply(200, users);
}

function renderUsersPage() {
  return render(
    <MantineProvider theme={theme}>
      <QueryClientProvider client={createQueryClient()}>
        <UsersPage />
      </QueryClientProvider>
    </MantineProvider>,
  );
}

function rowFor(email: string): HTMLElement {
  return screen.getByText(email).closest('tr') as HTMLElement;
}

describe('UsersPage', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('lists every account, including disabled ones, showing Nunca for a never-logged-in account', async () => {
    mockUsersList([adminUser, neverLoggedInUser, disabledUser]);
    renderUsersPage();

    expect(await screen.findByText(adminUser.email)).toBeInTheDocument();
    expect(screen.getByText(neverLoggedInUser.email)).toBeInTheDocument();
    expect(screen.getByText(disabledUser.email)).toBeInTheDocument();

    expect(
      within(rowFor(neverLoggedInUser.email)).getByText(strings.admin.users.never),
    ).toBeInTheDocument();
    expect(
      within(rowFor(disabledUser.email)).getByText(strings.admin.users.stateDisabled),
    ).toBeInTheDocument();
    expect(within(rowFor(adminUser.email)).getByText(strings.roleLabel.ADMIN)).toBeInTheDocument();
  });

  it('creates an account and reveals the generated password once', async () => {
    const user = userEvent.setup();
    mockUsersList([adminUser]);
    renderUsersPage();
    await screen.findByText(adminUser.email);

    nock(BASE_URL)
      .post('/api/admin/users', { email: 'new.user@conxugal.gal', role: 'USER' })
      .reply(201, {
        id: 'user-99',
        email: 'new.user@conxugal.gal',
        role: 'USER',
        enabled: true,
        createdAt: '2026-07-25T09:00:00Z',
        initialPassword: 'Tg7#kLp2Qw9$mZxR',
      });

    await user.click(screen.getByRole('button', { name: strings.admin.users.createButton }));
    await user.type(
      await screen.findByRole('textbox', { name: strings.admin.users.emailLabel }),
      'new.user@conxugal.gal',
    );
    await user.click(screen.getByRole('button', { name: strings.admin.users.submit }));

    expect(await screen.findByDisplayValue('Tg7#kLp2Qw9$mZxR')).toBeInTheDocument();
    expect(screen.getByText(strings.admin.users.passwordWarning)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: strings.admin.users.done }));

    expect(screen.queryByDisplayValue('Tg7#kLp2Qw9$mZxR')).not.toBeInTheDocument();
    expect(screen.getByText('new.user@conxugal.gal')).toBeInTheDocument();
  });

  it('rejects an empty or malformed email client-side without calling the API', async () => {
    const user = userEvent.setup();
    mockUsersList([adminUser]);
    renderUsersPage();
    await screen.findByText(adminUser.email);

    const createScope = nock(BASE_URL).post('/api/admin/users').reply(201);

    await user.click(screen.getByRole('button', { name: strings.admin.users.createButton }));
    await user.click(await screen.findByRole('button', { name: strings.admin.users.submit }));

    expect(await screen.findByText(strings.admin.users.emailRequired)).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', { name: strings.admin.users.emailLabel }),
      'not-an-email',
    );
    await user.click(screen.getByRole('button', { name: strings.admin.users.submit }));

    expect(await screen.findByText(strings.admin.users.emailInvalid)).toBeInTheDocument();
    expect(createScope.isDone()).toBe(false);
  });

  it('surfaces a duplicate-email create as a form error without changing the existing account', async () => {
    const user = userEvent.setup();
    mockUsersList([adminUser]);
    renderUsersPage();
    await screen.findByText(adminUser.email);

    nock(BASE_URL)
      .post('/api/admin/users')
      .reply(409, {
        type: 'urn:conxugal:problem-type:duplicate-email',
        title: 'Conflict',
        status: 409,
        detail: `An account with email ${adminUser.email} already exists.`,
      });

    await user.click(screen.getByRole('button', { name: strings.admin.users.createButton }));
    await user.type(
      await screen.findByRole('textbox', { name: strings.admin.users.emailLabel }),
      adminUser.email,
    );
    await user.click(screen.getByRole('button', { name: strings.admin.users.submit }));

    expect(await screen.findByText(strings.admin.users.duplicateEmailError)).toBeInTheDocument();
    expect(screen.getAllByText(adminUser.email)).toHaveLength(1);
  });

  it('disables then re-enables an account, reflecting the new state', async () => {
    const user = userEvent.setup();
    const target: UserAccount = { ...neverLoggedInUser };
    mockUsersList([target]);
    renderUsersPage();
    await screen.findByText(target.email);

    nock(BASE_URL)
      .post(`/api/admin/users/${target.id}/enabled`, { enabled: false })
      .reply(200, { ...target, enabled: false });

    await user.click(screen.getByRole('button', { name: strings.admin.users.disable }));

    expect(
      await screen.findByRole('button', { name: strings.admin.users.enable }),
    ).toBeInTheDocument();
    expect(screen.getByText(strings.admin.users.stateDisabled)).toBeInTheDocument();

    nock(BASE_URL)
      .post(`/api/admin/users/${target.id}/enabled`, { enabled: true })
      .reply(200, { ...target, enabled: true });

    await user.click(screen.getByRole('button', { name: strings.admin.users.enable }));

    expect(
      await screen.findByRole('button', { name: strings.admin.users.disable }),
    ).toBeInTheDocument();
    expect(screen.getByText(strings.admin.users.stateEnabled)).toBeInTheDocument();
  });

  it('prevents disabling the only remaining enabled administrator', async () => {
    const user = userEvent.setup();
    mockUsersList([adminUser]);
    renderUsersPage();
    await screen.findByText(adminUser.email);

    const disableButton = screen.getByRole('button', { name: strings.admin.users.disable });
    expect(disableButton).toHaveAttribute('aria-disabled', 'true');

    await user.click(disableButton);

    expect(screen.getByRole('button', { name: strings.admin.users.disable })).toBeInTheDocument();
  });
});
