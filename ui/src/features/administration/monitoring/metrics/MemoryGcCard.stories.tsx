import type { Meta, StoryObj } from '@storybook/react-vite';

import { MemoryGcCard } from './MemoryGcCard';
import { EMPTY_SAMPLE, LATEST } from './storyFixtures';

/**
 * Heap, non-heap, garbage collection and uptime. The progress bar is `aria-hidden`
 * on purpose: the figure beside it already says the same thing, and a second
 * announcement of it would only be noise.
 */
const meta = {
  component: MemoryGcCard,
  tags: ['autodocs'],
  args: { latest: LATEST },
} satisfies Meta<typeof MemoryGcCard>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Healthy: Story = {};

export const HeapUnderPressure: Story = {
  args: {
    latest: {
      ...LATEST,
      jvm: { ...LATEST.jvm, heapUsedBytes: 486 * 1024 * 1024, heapMaxBytes: 512 * 1024 * 1024 },
    },
  },
};

/** Freshly started, so uptime and the collection counters are near zero. */
export const JustStarted: Story = {
  args: {
    latest: {
      ...LATEST,
      jvm: {
        ...LATEST.jvm,
        uptimeMillis: 42_000,
        gcCollectionCount: 1,
        gcCollectionTimeMillis: 18,
      },
    },
  },
};

export const NoJvmReported: Story = {
  args: { latest: EMPTY_SAMPLE },
};

export const NoSample: Story = {
  args: { latest: null },
};
