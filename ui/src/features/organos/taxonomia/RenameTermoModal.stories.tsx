import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { SANIDADE } from '../storyFixtures';
import { RenameTermoModal } from './RenameTermoModal';

/**
 * Renaming a term. The field opens on the current name, and a duplicate name is
 * reported on the input rather than as a banner — it is the one refusal with a
 * field to attach to.
 */
const meta = {
  component: RenameTermoModal,
  tags: ['autodocs'],
  args: {
    opened: true,
    termo: SANIDADE,
    onRenamed: fn(),
    onCancel: fn(),
  },
} satisfies Meta<typeof RenameTermoModal>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Renaming: Story = {};
