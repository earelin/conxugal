import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { VIEW } from '../storyFixtures';
import { CreateTermoModal } from './CreateTermoModal';

/**
 * Creating a term. The open term is offered as the parent so the common case is
 * one click; changing the parent clears any refusal, since both refusals a
 * create can hit are about the parent — a name is only duplicate among a given
 * parent's children.
 *
 * Submitting reaches `POST /api/admin/organos/taxonomia/termos`, which Storybook
 * does not serve, so the write reports the generic refusal.
 */
const meta = {
  component: CreateTermoModal,
  tags: ['autodocs'],
  args: {
    opened: true,
    roots: VIEW.roots,
    defaultParentId: null,
    onCreated: fn(),
    onCancel: fn(),
  },
} satisfies Meta<typeof CreateTermoModal>;

export default meta;

type Story = StoryObj<typeof meta>;

/** From the root of the tree: the parent field opens empty. */
export const AtTheRoot: Story = {};

/** From an open term, which is offered as the parent. */
export const UnderTheOpenTerm: Story = {
  args: { defaultParentId: 't-2' },
};
