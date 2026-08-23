import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { strings } from '../../../shared/lib/strings';
import { TermoRefusalAlert } from './TermoRefusalAlert';

const copy = strings.admin.organos.termo;

/**
 * A refusal, rendered with the title the rule it broke earned — or nothing at
 * all when there is none. The refresh button appears only for the refusals that
 * are about another administrator's edit, because re-reading the section is the
 * only thing that can make the choice possible.
 */
const meta = {
  component: TermoRefusalAlert,
  tags: ['autodocs'],
} satisfies Meta<typeof TermoRefusalAlert>;

export default meta;

type Story = StoryObj<typeof meta>;

/** No title: the message stands on its own. */
export const Untitled: Story = {
  args: { refusal: { message: copy.genericError } },
};

/** A term cannot land inside its own branch. */
export const Cycle: Story = {
  args: {
    refusal: {
      title: copy.cycleTitle,
      message: copy.cycleUnderSelf('Consellería de Sanidade'),
    },
  },
};

export const HasChildren: Story = {
  args: {
    refusal: {
      title: copy.hasChildrenTitle,
      message: copy.hasChildrenOther(2, 'Consellería de Sanidade», «Consellería de Educación'),
    },
  },
};

/**
 * A stale record: the way out is the re-read the alert itself offers. Only the
 * placement refusals arrive with one — the four term dialogs pass no `onRefresh`,
 * which is why this uses the assign vocabulary rather than the term one.
 */
export const Refreshable: Story = {
  args: { refusal: { message: strings.admin.organos.assign.termoNotFound }, onRefresh: fn() },
};

/** Nothing refused, which renders nothing — the canvas is empty on purpose. */
export const NoRefusal: Story = {
  args: { refusal: null },
};
