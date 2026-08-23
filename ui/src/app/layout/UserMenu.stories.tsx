import type { Meta, StoryObj } from '@storybook/react-vite';
import { expect, userEvent, within } from 'storybook/test';

import type { CurrentUser } from '../../shared/entities/currentUser';
import { strings } from '../../shared/lib/strings';
import { UserMenu } from './UserMenu';

const ADMIN: CurrentUser = {
  id: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  email: 'admin@conxugal.gal',
  role: 'ADMIN',
  createdAt: '2026-01-15T09:30:00Z',
  lastLoginAt: '2026-07-10T08:12:00Z',
};

/**
 * The session control in the header. Open it to see the account block and the
 * sign-out item; the identity beside the avatar is hidden below `sm`, so the
 * viewport toolbar is worth a turn here.
 *
 * Signing out is a real mutation and Storybook serves no `/logout`, so pressing
 * it reports the failure alert rather than ending anything.
 */
const meta = {
  component: UserMenu,
  tags: ['autodocs'],
  args: { currentUser: ADMIN },
} satisfies Meta<typeof UserMenu>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Administrator: Story = {};

/**
 * Dropped down, which is where the account block and *Pechar sesión* live — the
 * closed trigger is otherwise all four stories have in common.
 */
export const Open: Story = {
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);
    await step('open the menu', async () => {
      await userEvent.click(canvas.getByRole('button', { name: strings.userMenu.trigger }));
      await expect(await canvas.findByText(strings.userMenu.logout)).toBeVisible();
    });
  },
};

/** The same control for a plain account — only the role label differs. */
export const StandardUser: Story = {
  args: { currentUser: { ...ADMIN, email: 'persoa.usuaria@conxugal.gal', role: 'USER' } },
};

/** A long address, which is what squeezes the header on a narrow viewport. */
export const LongEmail: Story = {
  args: {
    currentUser: { ...ADMIN, email: 'nome.apelido.moi.longo@administracion.xunta.gal' },
  },
};
