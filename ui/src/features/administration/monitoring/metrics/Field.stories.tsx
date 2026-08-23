import { Box } from '@mantine/core';
import type { Meta, StoryObj } from '@storybook/react-vite';

import { formatCount } from '../../../../shared/lib/number';
import { strings } from '../../../../shared/lib/strings';
import { Field } from './Field';

const t = strings.admin.dashboard.metrics;

/**
 * A labelled figure, used throughout the metrics detail cards. The value is a
 * `ReactNode` rather than a string so a caller can hand it a badge or an icon,
 * but every use in the panel today passes formatted text.
 */
const meta = {
  component: Field,
  tags: ['autodocs'],
} satisfies Meta<typeof Field>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Value: Story = {
  args: { label: t.totalRequestsLabel, value: formatCount(48_210) },
};

/** What the cards show when a sample carries nothing for this figure. */
export const NoValue: Story = {
  args: { label: t.gcTimeLabel, value: t.noValue },
};

/**
 * The longest label the panel actually uses, in a column no wider than the one
 * the cards lay these out in — `Field` constrains nothing itself, so without the
 * decorator nothing wraps at any real length.
 */
export const LongLabel: Story = {
  args: { label: t.gcCollectionsLabel, value: formatCount(118) },
  decorators: [
    (Story) => (
      <Box w={140}>
        <Story />
      </Box>
    ),
  ],
};
