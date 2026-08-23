import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { SANIDADE, sergas, VIEW, vivenda } from '../storyFixtures';
import { AssignOrganoModal } from './AssignOrganoModal';

/**
 * Filing an Órgano under a term. The entry point settles one half of the pair
 * and the dialog asks for the other: from a worklist row the Órgano is known
 * and the term is the question, from a term's header it is the other way round.
 * The `target` union is what makes "both" and "neither" unexpressible.
 */
const meta = {
  component: AssignOrganoModal,
  tags: ['autodocs'],
  args: {
    opened: true,
    view: VIEW,
    target: { kind: 'organo', organo: vivenda },
    onAssigned: fn(),
    onCancel: fn(),
    onRefresh: fn(),
  },
} satisfies Meta<typeof AssignOrganoModal>;

export default meta;

type Story = StoryObj<typeof meta>;

/** From the worklist: the Órgano is stated, the tree picker asks for the term. */
export const ChoosingATerm: Story = {};

/** An Órgano already filed, whose current placement the note names. */
export const Reassigning: Story = {
  args: { target: { kind: 'organo', organo: sergas } },
};

/** From a term's header: the term is stated and the catalogue is the question. */
export const ChoosingAnOrgano: Story = {
  args: { target: { kind: 'termo', termo: SANIDADE } },
};
