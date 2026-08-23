import type { Meta, StoryObj } from '@storybook/react-vite';

import { LoadingIndicator } from './LoadingIndicator';

/**
 * The spinner is decorative; the live region and its visually hidden label are
 * the part that makes the wait perceivable rather than merely visible. Read the
 * accessibility panel rather than the canvas to see what this component adds.
 */
const meta = {
  component: LoadingIndicator,
  tags: ['autodocs'],
} satisfies Meta<typeof LoadingIndicator>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Waiting: Story = {};
