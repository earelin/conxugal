import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { universidade, vivenda } from '../storyFixtures';
import { MarkOrganoModal } from './MarkOrganoModal';

/**
 * The confirmation that turning a row's switch on opens. It exists because
 * *Marcar e importar* is the honest name for what the write does: a multi-day
 * walk that refuses every other import while it runs, which is what the four
 * points list.
 *
 * No other action in the admin area costs days — unmarking asks nothing,
 * because what it stops is a load and what is stored stays put.
 */
const meta = {
  component: MarkOrganoModal,
  tags: ['autodocs'],
  args: {
    opened: true,
    organo: vivenda,
    onMarked: fn(),
    onCancel: fn(),
  },
} satisfies Meta<typeof MarkOrganoModal>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Confirming: Story = {};

/** The longest name in the catalogue, which the intro sentence carries inline. */
export const LongOrganoName: Story = {
  args: { organo: universidade },
};
