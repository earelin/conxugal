import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { strings } from '../../../shared/lib/strings';
import { VIEW } from '../storyFixtures';
import { TermoParentSelect } from './TermoParentSelect';

const copy = strings.admin.organos.termo;

/**
 * Every term as one flat list, each labelled with its full path — names repeat
 * across branches, so the path is what tells two options apart.
 *
 * Nothing is filtered out: a destination that would be refused stays pickable
 * so the cycle refusal can explain itself rather than leaving an administrator
 * hunting for a missing option.
 */
const meta = {
  component: TermoParentSelect,
  tags: ['autodocs'],
  args: {
    roots: VIEW.roots,
    label: copy.createParentLabel,
    value: null,
    onChange: fn(),
  },
} satisfies Meta<typeof TermoParentSelect>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * As the create dialog uses it: no root option, so an empty field means the
 * root and the hint underneath says as much.
 */
export const CreatingATerm: Story = {
  args: {
    placeholder: copy.createParentPlaceholder,
    description: copy.createParentHelp,
  },
};

/**
 * As the move dialog uses it: the root is a named destination rather than an
 * empty value, since moving a term to the root is a place to put it.
 */
export const MovingATerm: Story = {
  args: {
    label: copy.moveParentLabel,
    rootOptionLabel: copy.moveParentRoot,
    required: true,
  },
};

export const WithSelection: Story = {
  args: { value: 't-4' },
};
