import type { Meta, StoryObj } from '@storybook/react-vite';

import type { UserAccount } from './users';
import { UsersTable } from './UsersTable';

const admin: UserAccount = {
  id: 'u-1',
  email: 'admin@conxugal.gal',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-01-15T09:30:00Z',
  lastLoginAt: '2026-07-10T08:12:00Z',
};

const segundaAdmin: UserAccount = {
  id: 'u-2',
  email: 'direccion@conxugal.gal',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-02-02T11:05:00Z',
  lastLoginAt: '2026-08-01T16:40:00Z',
};

/** Never signed in, which the last column italicises rather than leaves blank. */
const recente: UserAccount = {
  id: 'u-3',
  email: 'nova.persoa@conxugal.gal',
  role: 'USER',
  enabled: true,
  createdAt: '2026-08-20T08:00:00Z',
  lastLoginAt: null,
};

const desactivada: UserAccount = {
  id: 'u-4',
  email: 'baixa@conxugal.gal',
  role: 'USER',
  enabled: false,
  createdAt: '2026-03-11T13:22:00Z',
  lastLoginAt: '2026-05-30T09:01:00Z',
};

/**
 * The account list. Accounts are never deleted, only disabled, and a disabled
 * row is dimmed rather than removed — which is why the table has no empty
 * state worth storing beyond the one below.
 */
const meta = {
  component: UsersTable,
  tags: ['autodocs'],
  args: { users: [admin, segundaAdmin, recente, desactivada] },
} satisfies Meta<typeof UsersTable>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Accounts: Story = {};

/**
 * One enabled administrator left. Their *Desactivar* button is held disabled
 * with a tooltip saying why, so the last way in cannot be closed by accident.
 */
export const LastEnabledAdmin: Story = {
  args: { users: [admin, recente, desactivada] },
};

export const NoAccounts: Story = {
  args: { users: [] },
};
