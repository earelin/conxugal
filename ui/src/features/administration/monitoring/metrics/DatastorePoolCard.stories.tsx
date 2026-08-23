import type { Meta, StoryObj } from '@storybook/react-vite';

import { DatastorePoolCard } from './DatastorePoolCard';
import { EMPTY_SAMPLE, LATEST } from './storyFixtures';

/**
 * The connection pool as one bar: in use, idle, free. The bar is the whole
 * point of the card, so the stories are the shapes it can take.
 */
const meta = {
  component: DatastorePoolCard,
  tags: ['autodocs'],
  args: { latest: LATEST },
} satisfies Meta<typeof DatastorePoolCard>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Healthy: Story = {};

/** Every connection handed out, which is the shape worth spotting early. */
export const Saturated: Story = {
  args: { latest: { ...LATEST, datastorePool: { active: 10, idle: 0, max: 10 } } },
};

export const Idle: Story = {
  args: { latest: { ...LATEST, datastorePool: { active: 0, idle: 2, max: 10 } } },
};

/**
 * A sample carrying no pool figures at all. The bar falls back to a flat grey
 * track rather than disappearing, so the card keeps its height.
 */
export const NoPoolReported: Story = {
  args: { latest: EMPTY_SAMPLE },
};

/** Before the first sample arrives. */
export const NoSample: Story = {
  args: { latest: null },
};
