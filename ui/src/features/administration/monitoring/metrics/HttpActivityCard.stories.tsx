import type { Meta, StoryObj } from '@storybook/react-vite';

import { HttpActivityCard } from './HttpActivityCard';
import { EMPTY_SAMPLE, LATEST } from './storyFixtures';

/**
 * Requests and errors since startup, with the error rate badged by severity.
 * The badge is classified on the rounded figure the reader actually sees, so
 * two rates rendering the same percentage can never carry different colours —
 * the three stories below sit either side of those thresholds.
 */
const meta = {
  component: HttpActivityCard,
  tags: ['autodocs'],
  args: { latest: LATEST },
} satisfies Meta<typeof HttpActivityCard>;

export default meta;

type Story = StoryObj<typeof meta>;

export const NormalErrorRate: Story = {
  args: { latest: { ...LATEST, http: { requestCount: 48_210, errorCount: 96 } } },
};

export const ElevatedErrorRate: Story = {
  args: { latest: { ...LATEST, http: { requestCount: 10_000, errorCount: 200 } } },
};

export const HighErrorRate: Story = {
  args: { latest: { ...LATEST, http: { requestCount: 10_000, errorCount: 900 } } },
};

/** No requests yet, so there is no rate to classify and no badge to show. */
export const NoTrafficYet: Story = {
  args: { latest: { ...LATEST, http: { requestCount: 0, errorCount: 0 } } },
};

export const NoHttpReported: Story = {
  args: { latest: EMPTY_SAMPLE },
};

export const NoSample: Story = {
  args: { latest: null },
};
