import type { Meta, StoryObj } from '@storybook/react-vite';

import { MetricsFooterNotes } from './MetricsFooterNotes';

/**
 * The two standing notes under the metrics panel: what the panel does not
 * collect, and how long its history runs. Both are fixed copy, so there is one
 * story — the sample count comes from `METRICS_HISTORY_LIMIT`, not from props.
 */
const meta = {
  component: MetricsFooterNotes,
  tags: ['autodocs'],
} satisfies Meta<typeof MetricsFooterNotes>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Notes: Story = {};
