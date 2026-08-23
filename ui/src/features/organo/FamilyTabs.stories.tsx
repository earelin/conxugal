import { Card, Text } from '@mantine/core';
import type { Meta, StoryObj } from '@storybook/react-vite';

import { FAMILIES, type Family } from './families';
import { FamilyTabs } from './FamilyTabs';

const contratosMenores = FAMILIES[0];

/**
 * A second family, which the registry does not hold yet. It is written here so
 * the bar can be seen with more than one tab — the shape the component is
 * built for, and the one no route can show today.
 */
const licitacions: Family = {
  key: 'licitacions',
  path: 'licitacions',
  label: 'Licitacións',
};

/**
 * The bar that says what an Órgano's page holds. Each tab is a real link to
 * that family's route, so the bar mirrors the URL rather than owning it.
 *
 * Arrow keys move focus without opening anything — opening a family mounts a
 * section and reads its contracts, so arrowing across the bar would fetch every
 * family in between. Enter and Space are what choose.
 */
const meta = {
  component: FamilyTabs,
  tags: ['autodocs'],
  args: {
    basePath: '/organo/o-1',
    held: [contratosMenores],
    active: contratosMenores,
    children: (
      <Card withBorder radius="md" padding="lg">
        <Text c="dimmed">A sección da familia activa monta aquí.</Text>
      </Card>
    ),
  },
} satisfies Meta<typeof FamilyTabs>;

export default meta;

type Story = StoryObj<typeof meta>;

/** One family still draws the whole bar: the next joins it, unchanged. */
export const SingleFamily: Story = {};

export const SeveralFamilies: Story = {
  args: { held: [contratosMenores, licitacions] },
};

/** The second tab active, which is what a deep link into it arrives as. */
export const SecondFamilyActive: Story = {
  args: { held: [contratosMenores, licitacions], active: licitacions },
};
