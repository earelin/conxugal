import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { buildTaxonomiaView } from '../../../shared/lib/taxonomiaTree';
import { ORGANOS, TERMOS, VIEW } from '../storyFixtures';
import { TaxonomiaTreeCard } from './TaxonomiaTreeCard';

const termoActions = { onRename: fn(), onMove: fn(), onDelete: fn(), onAssign: fn() };

/**
 * The taxonomía as the administration section navigates it: a tree of terms
 * with their counts, the unclassified worklist beneath, and *Novo termo*.
 *
 * The open term trades its count badge for its three actions rather than
 * adding to the row — three icons plus a badge do not fit beside a term name at
 * 360 px, and the count is on screen in the pane beside it anyway.
 */
const meta = {
  component: TaxonomiaTreeCard,
  tags: ['autodocs'],
  args: {
    roots: VIEW.roots,
    unclassified: VIEW.unclassified,
    selectedTermoId: null,
    onSelect: fn(),
    termoActions,
  },
} satisfies Meta<typeof TaxonomiaTreeCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** Nothing chosen, so the worklist is what the pane beside it would show. */
export const WorklistSelected: Story = {};

/** An open term, carrying its three actions in place of its count. */
export const TermoSelected: Story = {
  args: { selectedTermoId: 't-2' },
};

/**
 * Every Órgano filed, so the worklist entry reads zero. The tree is rebuilt from
 * a catalogue with nothing unclassified rather than just emptying the prop: the
 * page derives both from one build, so a bare `unclassified: []` would lose an
 * Órgano from the counts entirely.
 */
export const NothingUnclassified: Story = {
  args: (() => {
    const filed = buildTaxonomiaView(
      TERMOS,
      ORGANOS.map((organo) => (organo.termoId === null ? { ...organo, termoId: 't-5' } : organo)),
    );
    return { roots: filed.roots, unclassified: filed.unclassified };
  })(),
};

/** Before the first term is created. */
export const EmptyTaxonomia: Story = {
  args: { roots: [] },
};
