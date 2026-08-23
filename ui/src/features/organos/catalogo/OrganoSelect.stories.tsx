import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { strings } from '../../../shared/lib/strings';
import { VIEW } from '../storyFixtures';
import { OrganoSelect } from './OrganoSelect';

const copy = strings.admin.organos.assign;

/**
 * Picks one Órgano out of the catalogue, each option stating where it is filed
 * now — which is what makes a reassignment legible before it is confirmed.
 *
 * The filter is accent-insensitive and matches the name only: typing *saude*
 * finds *Servizo Galego de Saúde*, and an option's placement never matches, so
 * the Órgano being typed is not buried under everything in the same branch.
 */
const meta = {
  component: OrganoSelect,
  tags: ['autodocs'],
  args: {
    organos: VIEW.catalogue,
    roots: VIEW.roots,
    label: copy.organoLabel,
    value: null,
    onChange: fn(),
    required: true,
  },
} satisfies Meta<typeof OrganoSelect>;

export default meta;

type Story = StoryObj<typeof meta>;

/** Open the dropdown to see the placements, and type to filter. */
export const Choosing: Story = {};

/** Picked: the closed field shows the bare name, never the whole path. */
export const WithSelection: Story = {
  args: { value: 'o-1' },
};

/** An empty catalogue, where the field has nothing to offer. */
export const EmptyCatalogue: Story = {
  args: { organos: [] },
};
