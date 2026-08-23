import type { Meta, StoryObj } from '@storybook/react-vite';
import { useArgs } from 'storybook/preview-api';
import { fn } from 'storybook/test';

import { PageJump } from './PageJump';

/**
 * The jump to a chosen page, normally seen between `Pagination`'s two pairs of
 * buttons. Enter commits and blur reverts, so focus leaving the box never
 * navigates; a page outside `1…totalPages` is refused rather than clamped —
 * paging somewhere adjacent to what was asked for is worse than not moving.
 *
 * The stories write the chosen page back into `page`, which is what makes the
 * refusal legible: a page inside the range sticks, one outside it snaps back.
 * Against a static `page` the two would look identical.
 */
const meta = {
  component: PageJump,
  tags: ['autodocs'],
  args: { onPageChange: fn() },
  render: function Render(args) {
    const [, updateArgs] = useArgs<typeof args>();
    return (
      <PageJump
        {...args}
        onPageChange={(page) => {
          args.onPageChange(page);
          updateArgs({ page });
        }}
      />
    );
  },
} satisfies Meta<typeof PageJump>;

export default meta;

type Story = StoryObj<typeof meta>;

/** Type 12 and press Enter; then try 0 or 38 and watch the box snap back. */
export const Enabled: Story = {
  args: { page: 3, totalPages: 37, disabled: false },
};

/** How the box looks when the selection is a single page. */
export const Disabled: Story = {
  args: { page: 1, totalPages: 1, disabled: true },
};

/** A page total wide enough for `formatCount` to group — the copy groups nothing. */
export const LargeSelection: Story = {
  args: { page: 517, totalPages: 1284, disabled: false },
};
