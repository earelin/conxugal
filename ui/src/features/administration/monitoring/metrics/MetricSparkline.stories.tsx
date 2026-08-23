import type { Meta, StoryObj } from '@storybook/react-vite';

import { heapUsedPercent } from './metricsFormat';
import { MetricSparkline } from './MetricSparkline';
import { HISTORY, SHORT_HISTORY } from './storyFixtures';

/**
 * The line under a metric tile. Decorative in every state — the figure above it
 * is the accessible source of truth — so it is `inert` and `aria-hidden`, which
 * is also what strips the `role="application"` recharts would otherwise expose.
 *
 * The series is always padded out to the full history limit, so an early run
 * draws its samples at the left rather than stretching them across the width.
 */
const meta = {
  component: MetricSparkline,
  tags: ['autodocs'],
  args: {
    history: HISTORY,
    state: 'live',
    select: (sample) => heapUsedPercent(sample),
  },
} satisfies Meta<typeof MetricSparkline>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Live: Story = {};

/** Paler while the stream is down, so a stale line reads as stale. */
export const Reconnecting: Story = {
  args: { state: 'reconnecting' },
};

/** A handful of samples, drawn against the width the full run will fill. */
export const FillingHistory: Story = {
  args: { history: SHORT_HISTORY },
};

/** Nothing to draw yet: the box keeps its height and says so instead. */
export const NoHistory: Story = {
  args: { history: [], state: 'connecting' },
};
