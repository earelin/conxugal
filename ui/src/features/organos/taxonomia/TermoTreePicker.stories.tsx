import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { strings } from '../../../shared/lib/strings';
import { VIEW } from '../storyFixtures';
import { TermoTreePicker } from './TermoTreePicker';

const copy = strings.admin.organos.assign;

/**
 * The taxonomía as a searchable, indented list of destinations — deliberately
 * not Mantine's `Tree`, since a picker has nothing to expand or collapse.
 *
 * Type into the search to narrow it; the list keeps one tab stop and the arrows
 * move between rows inside it. There is no "no term" row: taking an Órgano out
 * of the taxonomía is a different action on a different screen.
 */
const meta = {
  component: TermoTreePicker,
  tags: ['autodocs'],
  args: {
    roots: VIEW.roots,
    label: copy.termoLabel,
    value: null,
    onChange: fn(),
    required: true,
  },
} satisfies Meta<typeof TermoTreePicker>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Choosing: Story = {};

/** The chosen row carries the check and is where the list's tab stop lands. */
export const WithSelection: Story = {
  args: { value: 't-2' },
};

/**
 * No terms at all, which is the normal state before the first one is created —
 * not a failed search, and the panel says so.
 */
export const EmptyTaxonomia: Story = {
  args: { roots: [] },
};
