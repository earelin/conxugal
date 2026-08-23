import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { CONSELLERIAS, EDUCACION, SANIDADE, VIEW } from '../storyFixtures';
import { MoveTermoModal } from './MoveTermoModal';

/**
 * Moving a term. The destination list keeps every term, including the ones a
 * move would be refused for — the term itself and its own descendants — so the
 * cycle refusal can explain the rule where the destination is chosen rather
 * than leaving an option mysteriously missing.
 */
const meta = {
  component: MoveTermoModal,
  tags: ['autodocs'],
  args: {
    opened: true,
    roots: VIEW.roots,
    termo: SANIDADE,
    parentId: CONSELLERIAS.id,
    onMoved: fn(),
    onCancel: fn(),
  },
} satisfies Meta<typeof MoveTermoModal>;

export default meta;

type Story = StoryObj<typeof meta>;

/** Opens showing where the term sits now. */
export const Moving: Story = {};

/**
 * A term with a child, opened from its real place under *Consellerías*. Pick
 * *Educación* itself, or its child *Axencias*, to see the cycle refused before
 * anything is sent — *Mover* stays disabled while that stands.
 */
export const WithDescendants: Story = {
  args: { termo: EDUCACION, parentId: CONSELLERIAS.id },
};

/** A root term, where the field opens on *Na raíz da taxonomía*. */
export const AlreadyAtTheRoot: Story = {
  args: { termo: CONSELLERIAS, parentId: null },
};
