import type { Meta, StoryObj } from '@storybook/react-vite';
import { useArgs } from 'storybook/preview-api';
import { fn } from 'storybook/test';

import { Pagination } from './Pagination';

/**
 * The one paging control, for every paginated list in the system. It renders
 * the four numbers of the paged envelope in the base they arrive in, so the
 * stories below are the four numbers and nothing else.
 *
 * At the two ends the controls that would lead nowhere are disabled rather than
 * removed, so the control never changes shape under a reader mid-session. The
 * stories page for real: walking to either end is what shows focus dropping to
 * the box when the button that got there disables itself underneath it.
 */
const meta = {
  component: Pagination,
  tags: ['autodocs'],
  args: { onPageChange: fn() },
  render: function Render(args) {
    const [, updateArgs] = useArgs<typeof args>();
    return (
      <Pagination
        {...args}
        onPageChange={(page) => {
          args.onPageChange(page);
          updateArgs({ page });
        }}
      />
    );
  },
} satisfies Meta<typeof Pagination>;

export default meta;

type Story = StoryObj<typeof meta>;

/** The middle of a 37-page selection, so every control is live. */
export const MiddleOfSelection: Story = {
  args: { page: 3, size: 50, totalItems: 1832, totalPages: 37 },
};

/** Both backward controls disabled; the jump box still takes a page. */
export const FirstPage: Story = {
  args: { page: 1, size: 50, totalItems: 1832, totalPages: 37 },
};

export const LastPage: Story = {
  args: { page: 37, size: 50, totalItems: 1832, totalPages: 37 },
};

/**
 * One page still draws the whole control: how many entries the selection holds
 * is worth saying whether or not there is anywhere to page to.
 */
export const SinglePage: Story = {
  args: { page: 1, size: 50, totalItems: 12, totalPages: 1 },
};

/** Nothing matched, so the page count the wire serves is zero. */
export const NoResults: Story = {
  args: { page: 1, size: 50, totalItems: 0, totalPages: 0 },
};
