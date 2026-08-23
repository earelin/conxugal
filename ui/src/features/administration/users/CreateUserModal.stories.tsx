import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { CreateUserModal } from './CreateUserModal';

/**
 * Creating an account, in two acts: the form, and then the generated password
 * shown once with a copy button. The second act is reachable only through a
 * successful `POST /api/admin/users`, which Storybook does not serve — so this
 * story stops at the form, and submitting it reports a failure.
 */
const meta = {
  component: CreateUserModal,
  tags: ['autodocs'],
  args: { opened: true, onClose: fn() },
} satisfies Meta<typeof CreateUserModal>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Form: Story = {};
