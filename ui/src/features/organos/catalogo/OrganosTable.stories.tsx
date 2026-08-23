import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { strings } from '../../../shared/lib/strings';
import { ORGANOS, SANIDADE, SANIDADE_ORGANOS, sergas } from '../storyFixtures';
import { OrganosTable } from './OrganosTable';

const copy = strings.admin.organos;

/**
 * The administration catalogue's table, rendered both inside a term and in the
 * unclassified worklist. Which of the two it is showing is decided by one prop:
 * with `onClear` the row offers *Quitar do termo*, without it *Asignar*.
 *
 * Worth browsing at a narrow viewport — below `sm` the row action drops its
 * label to the icon alone and the table scrolls inside its own region.
 */
const meta = {
  component: OrganosTable,
  tags: ['autodocs'],
  args: {
    organos: SANIDADE_ORGANOS,
    label: SANIDADE.name,
    emptyMessage: copy.termEmpty,
    actions: {
      onAssign: fn(),
      onClear: fn(),
      mark: { onMark: fn(), onUnmark: fn() },
    },
  },
} satisfies Meta<typeof OrganosTable>;

export default meta;

type Story = StoryObj<typeof meta>;

export const InsideATerm: Story = {};

/**
 * The worklist: no placement to clear, so every row offers to file its Órgano
 * instead. All six mark states are on show here.
 */
export const Worklist: Story = {
  args: {
    organos: ORGANOS,
    label: copy.unclassified,
    emptyMessage: copy.unclassifiedEmpty,
    actions: { onAssign: fn(), mark: { onMark: fn(), onUnmark: fn() } },
  },
};

/** Only the row whose clear is in flight spins. */
export const Clearing: Story = {
  args: {
    actions: {
      onAssign: fn(),
      onClear: fn(),
      clearingId: sergas.id,
      mark: { onMark: fn(), onUnmark: fn() },
    },
  },
};

/** Empty, where the table gives way to the message its caller supplied. */
export const Empty: Story = {
  args: { organos: [] },
};
