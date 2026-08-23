import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn, userEvent, within } from 'storybook/test';

import { buildTaxonomiaView } from '../../lib/taxonomiaTree';
import { OrganoPicker } from './OrganoPicker';
import { TERMOS, VIEW } from './storyFixtures';

/**
 * The reader's way into an Órgano, in the side panel of every page. Browsing
 * and searching are two states of one control over one list, not two
 * components — open the dropdown and type to move between them.
 *
 * The reads happen above it: every state below is a combination of the four
 * flags the shell passes down, which is why none of these stories fetches.
 *
 * The dropdown mounts only while open, so every story that has something to say
 * about the body opens it — closed, all of them are the same trigger.
 */
const meta = {
  component: OrganoPicker,
  tags: ['autodocs'],
  args: {
    view: VIEW,
    isPending: false,
    isFetching: false,
    isError: false,
    onRetry: fn(),
    onNavigate: fn(),
  },
} satisfies Meta<typeof OrganoPicker>;

export default meta;

type Story = StoryObj<typeof meta>;

/** Drops the control down, which is where every state below draws itself. */
const open: Story['play'] = async ({ canvasElement }) => {
  // The closed control holds one button and its name depends on which Órgano is
  // open, so it is reached by role rather than by name.
  await userEvent.click(within(canvasElement).getByRole('button'));
};

/** The tree. Type into the filter to cross over to the matches. */
export const Browsing: Story = { play: open };

/**
 * The trigger names the open Órgano, read from the route rather than held — the
 * only story that needs the picker to be somewhere in particular.
 */
export const OnAnOrgano: Story = {
  parameters: { initialPath: '/organo/o-1' },
};

/**
 * A route naming an Órgano whose catalogue has not landed yet: the trigger says
 * so rather than showing the placeholder it will never settle on.
 */
export const Loading: Story = {
  args: { view: null, isPending: true },
  parameters: { initialPath: '/organo/o-1' },
  play: open,
};

/**
 * A failure with nothing to show, which is the state a fresh session meets. A
 * refetch that fails while a view is already in hand keeps the tree instead.
 */
export const Failed: Story = {
  args: { view: null, isError: true },
  play: open,
};

export const Retrying: Story = {
  args: { view: null, isError: true, isFetching: true },
  play: open,
};

/**
 * Every term is empty, so pruning leaves nothing to browse — an empty
 * catalogue is an empty tree, and the control says so rather than dropping
 * down onto blank space.
 */
export const EmptyCatalogue: Story = {
  args: { view: buildTaxonomiaView(TERMOS, []) },
  play: open,
};
