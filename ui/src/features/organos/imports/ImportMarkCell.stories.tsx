import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { cunqueiro, innovacion, portos, sergas, universidade, vivenda } from '../storyFixtures';
import { ImportMarkCell } from './ImportMarkCell';

/**
 * One catalogue row's contratos menores: the mark, and what is stored under it.
 * The six stories below are the six states `markState` distinguishes.
 *
 * The tooltip is on the whole cell rather than the switch, because a disabled
 * switch fires no pointer events — hover or focus any of these to read it.
 */
const meta = {
  component: ImportMarkCell,
  tags: ['autodocs'],
  args: {
    organo: sergas,
    actions: { onMark: fn(), onUnmark: fn() },
  },
} satisfies Meta<typeof ImportMarkCell>;

export default meta;

type Story = StoryObj<typeof meta>;

/** Marked, nothing loaded yet. */
export const Marked: Story = {};

/** Marked, and an import that got part of the way. */
export const Partial: Story = {
  args: { organo: cunqueiro },
};

export const Imported: Story = {
  args: { organo: innovacion },
};

/**
 * Unmarked but still holding history. Grey rather than red: no longer being
 * updated is a decision, not a fault.
 */
export const Stale: Story = {
  args: { organo: universidade },
};

/** Nothing stored at all, which is the only state that shows the dash. */
export const Nothing: Story = {
  args: { organo: vivenda },
};

/** Inactive: the switch is blocked and the cell says why on screen, not only on hover. */
export const Ineligible: Story = {
  args: { organo: portos },
};

/** A write in flight locks that row's switch alone. */
export const Writing: Story = {
  args: { actions: { onMark: fn(), onUnmark: fn(), markingId: sergas.id } },
};
