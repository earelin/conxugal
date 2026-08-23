import type { Meta, StoryObj } from '@storybook/react-vite';
import { useState } from 'react';

import { MetricsSectionHeader } from './MetricsSectionHeader';

/**
 * The metrics panel's title and its connection badge. The three stream states
 * are the whole component: live pulses, reconnecting counts up from the last
 * sample, and connecting has nothing to report yet.
 *
 * `lastArrivedAt` is pinned to a fixed instant so the caption reads the same on
 * every visit — except under *Reconnecting*, which takes its anchor per mount
 * because the seconds counting up from it are the point.
 */
const LAST_SAMPLE = new Date('2026-08-23T09:41:12');

const meta = {
  component: MetricsSectionHeader,
  tags: ['autodocs'],
  args: { state: 'live', lastArrivedAt: LAST_SAMPLE },
} satisfies Meta<typeof MetricsSectionHeader>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Live: Story = {};

/** Nothing has arrived yet, so there is no time to name. */
export const Connecting: Story = {
  args: { state: 'connecting', lastArrivedAt: null },
};

/**
 * The caption counts up from the last sample, ticking once a second. The anchor
 * is taken per mount rather than at module load: pinned, it would open on
 * however many seconds the Storybook tab had been sitting there.
 */
export const Reconnecting: Story = {
  args: { state: 'reconnecting' },
  render: (args) => {
    const [lastArrivedAt] = useState(() => new Date(Date.now() - 8_000));
    return <MetricsSectionHeader {...args} lastArrivedAt={lastArrivedAt} />;
  },
};
