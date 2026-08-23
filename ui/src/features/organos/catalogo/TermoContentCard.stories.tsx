import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { findTermoPath } from '../../../shared/lib/taxonomiaTree';
import { VIEW } from '../storyFixtures';
import { TermoContentCard } from './TermoContentCard';

const termoActions = { onRename: fn(), onMove: fn(), onDelete: fn(), onAssign: fn() };

/**
 * The pane beside the taxonomía tree: the open term's Órganos, its breadcrumb
 * trail and its four actions — or the unclassified worklist, which is not a
 * term and so has none of them.
 *
 * Which of the two it shows is decided by one prop: an empty `openPath` selects
 * the worklist.
 */
const meta = {
  component: TermoContentCard,
  tags: ['autodocs'],
  args: {
    openPath: findTermoPath(VIEW.roots, 't-2'),
    unclassified: VIEW.unclassified,
    termoActions,
    onAssignOrgano: fn(),
    onMarkOrgano: fn(),
    onRefresh: fn(),
  },
} satisfies Meta<typeof TermoContentCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** A term two levels down, so the breadcrumb has something to say. */
export const OpenTerm: Story = {};

/** A term nested one level deeper again. */
export const DeeplyNestedTerm: Story = {
  args: { openPath: findTermoPath(VIEW.roots, 't-4') },
};

/**
 * The worklist. It has no name to change, no place in the tree and nothing to
 * delete, so the four term actions are absent rather than disabled.
 */
export const Worklist: Story = {
  args: { openPath: [] },
};

/** A term with nothing filed under it, which drops the count caption too. */
export const EmptyTerm: Story = {
  args: { openPath: findTermoPath(VIEW.roots, 't-5') },
};
