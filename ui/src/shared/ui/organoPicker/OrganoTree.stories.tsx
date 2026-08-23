import { Stack, Text } from '@mantine/core';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { strings } from '../../lib/strings';
import { pruneEmptyTermos } from '../../lib/taxonomiaTree';
import { OrganoTree } from './OrganoTree';
import { innovacion, VIEW, vivenda } from './storyFixtures';
import { toTreeData } from './treeData';

const copy = strings.organoPicker;

const LABEL_ID = 'organo-tree-story-label';

const DATA = toTreeData(pruneEmptyTermos(VIEW.roots), VIEW.unclassified);

/**
 * The browsing half of `OrganoPicker`, shown here outside its dropdown.
 *
 * Selection is presentational: the route says which Órgano is open and nothing
 * in the tree writes back to it, which is why `openId` is an ordinary prop.
 * Every branch opens on mount — the component is remounted whenever the
 * taxonomía changes shape, since Mantine's `useTree` fixes its expanded state
 * at creation and would otherwise show a later term shut.
 */
const meta = {
  component: OrganoTree,
  tags: ['autodocs'],
  args: {
    data: DATA,
    openId: null,
    onOpen: fn(),
    labelledBy: LABEL_ID,
  },
  // The tree is named by an element outside itself, so the stories have to
  // supply the one `labelledBy` points at or the accessible name is empty.
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
} satisfies Meta<typeof OrganoTree>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Browsing: Story = {};

/** The open Órgano carries the check and the selected row's colours. */
export const WithOpenOrgano: Story = {
  args: { openId: innovacion.id },
};

/**
 * An id the visible set no longer holds — a link shared before an Órgano's
 * last contract was withdrawn — simply marks nothing.
 */
export const OpenOrganoNotInTree: Story = {
  args: { openId: 'gone' },
};

/**
 * Nothing classified: the whole catalogue hangs at the root, which is the tree
 * an administrator meets before filing any of it.
 */
export const Unclassified: Story = {
  args: { data: toTreeData([], VIEW.catalogue), openId: vivenda.id },
};
