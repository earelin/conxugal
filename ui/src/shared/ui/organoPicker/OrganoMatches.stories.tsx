import { Stack, Text } from '@mantine/core';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { createRef } from 'react';
import { fn } from 'storybook/test';

import { strings } from '../../lib/strings';
import { OrganoMatches } from './OrganoMatches';
import { cunqueiro, innovacion, ORGANOS, vivenda } from './storyFixtures';

const copy = strings.organoPicker;

const LABEL_ID = 'organo-matches-story-label';

// The list owns a ref so its arrow keys can move focus between rows. A stable
// module-scope ref rather than a hook: args are plain data, and no two of these
// stories are ever alive at once.
const listRef = createRef<HTMLDivElement>();

/**
 * The filter's answer inside `OrganoPicker`: the Órganos whose names hold the
 * query, or the refusal when none do. It offers no paging, no sorting and no
 * count, and it never falls back to listing the catalogue.
 *
 * The label it is named by lives outside it — that is the picker's job in the
 * app, so the decorator stands in for that surrounding.
 */
const meta = {
  component: OrganoMatches,
  tags: ['autodocs'],
  args: {
    id: 'organo-matches-story',
    organos: ORGANOS,
    query: 'a',
    openId: null,
    onOpen: fn(),
    labelledBy: LABEL_ID,
    listRef,
  },
  decorators: [
    (Story) => (
      <Stack gap={4} w={320}>
        <Text id={LABEL_ID} size="xs" fw={700} c="dimmed" tt="uppercase" px="xs">
          {copy.label}
        </Text>
        <Story />
      </Stack>
    ),
  ],
} satisfies Meta<typeof OrganoMatches>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Matches: Story = {};

/**
 * The open Órgano is the one row that keeps a tab stop of its own. It is the
 * last row here on purpose: the fallback already puts the tab stop on the first
 * row, so an open first row would demonstrate nothing.
 */
export const WithOpenOrgano: Story = {
  args: { openId: vivenda.id },
};

/**
 * Inactive is a fact about the catalogue rather than about whether there is
 * anything to see, so the badge marks the row and the row stays selectable.
 */
export const InactiveOrgano: Story = {
  args: { organos: [cunqueiro, innovacion] },
};

/** Nothing matched: the query is quoted back rather than the list emptied. */
export const NoMatches: Story = {
  args: { organos: [], query: 'deputación' },
};
