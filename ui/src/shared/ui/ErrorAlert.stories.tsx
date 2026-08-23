import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { strings } from '../lib/strings';
import { ErrorAlert } from './ErrorAlert';

const copy = strings.organoPicker;

/**
 * The assertive counterpart of `StatusAlert`: this is a failure, so it keeps
 * the `role="alert"` Mantine gives an `Alert` by default.
 */
const meta = {
  component: ErrorAlert,
  tags: ['autodocs'],
  args: {
    title: copy.errorTitle,
    children: copy.errorHelp,
  },
} satisfies Meta<typeof ErrorAlert>;

export default meta;

type Story = StoryObj<typeof meta>;

/** No `onRetry`: nothing here can be re-asked, so no button is offered. */
export const WithoutRetry: Story = {};

export const Retryable: Story = {
  args: { onRetry: fn() },
};

/**
 * The state the `retrying` prop exists for. A failed query keeps reporting its
 * error while it refetches, so without the spinner a click looks like nothing
 * happened at all.
 */
export const Retrying: Story = {
  args: { onRetry: fn(), retrying: true },
};

/** Untitled, which is how it reads inside a menu or beside a form field. */
export const WithoutTitle: Story = {
  args: { title: undefined, children: strings.userMenu.logoutError },
};
