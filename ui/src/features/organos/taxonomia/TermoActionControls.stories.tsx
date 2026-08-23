import { Group } from '@mantine/core';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { SANIDADE } from '../storyFixtures';
import { TermoActionButtons, TermoActionIcons } from './TermoActionControls';

const handlers = { onRename: fn(), onMove: fn(), onDelete: fn(), onAssign: fn() };

/**
 * The writes that act on whichever term is open, in the two shapes they take:
 * labelled buttons in the content header, icon-only on the selected tree row
 * where a term name has already spent the width.
 *
 * *Asignar órgano* is deliberately absent from the icon set — three plus the
 * count badge is already what fits a 360 px row, and filing an Órgano is
 * reachable from the header and from every worklist row.
 */
const meta = {
  component: TermoActionButtons,
  subcomponents: { TermoActionIcons },
  tags: ['autodocs'],
  args: handlers,
} satisfies Meta<typeof TermoActionButtons>;

export default meta;

type Story = StoryObj<typeof meta>;

/** The header row. Below `sm` the four wrap onto a second line. */
export const Buttons: Story = {};

/**
 * The tree row's three. Each names itself and its term, since the same actions
 * also sit in the header above.
 */
export const Icons: Story = {
  render: (args) => (
    <Group gap={4}>
      <TermoActionIcons {...args} termoName={SANIDADE.name} />
    </Group>
  ),
};
