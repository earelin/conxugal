import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { EDUCACION, SANIDADE } from '../storyFixtures';
import { DeleteTermoModal } from './DeleteTermoModal';

/**
 * Deleting a term. Whether it can be deleted is answered from the tree the
 * section already holds, so the block is explained on open rather than after a
 * round trip that would refuse it anyway.
 */
const meta = {
  component: DeleteTermoModal,
  tags: ['autodocs'],
  args: {
    opened: true,
    termo: SANIDADE,
    onDeleted: fn(),
    onCancel: fn(),
  },
} satisfies Meta<typeof DeleteTermoModal>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * A term with no children. The note says what happens to the Órganos filed
 * under it — they return to the worklist rather than being deleted.
 */
export const Deletable: Story = {};

/**
 * A term holding a child. *Eliminar* is disabled and the refusal names what is
 * in the way, so nothing is sent.
 */
export const BlockedByChildren: Story = {
  args: { termo: EDUCACION },
};
