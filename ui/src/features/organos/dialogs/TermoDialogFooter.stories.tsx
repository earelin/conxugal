import { Button } from '@mantine/core';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { fn } from 'storybook/test';

import { strings } from '../../../shared/lib/strings';
import { TermoDialogFooter } from './TermoDialogFooter';

const copy = strings.admin.organos.termo;

/**
 * Every term dialog closes the same way, so the footer owns *Cancelar* and the
 * dialog supplies only its own primary as `children`.
 */
const meta = {
  component: TermoDialogFooter,
  tags: ['autodocs'],
  args: { onCancel: fn() },
} satisfies Meta<typeof TermoDialogFooter>;

export default meta;

type Story = StoryObj<typeof meta>;

export const WithPrimary: Story = {
  args: { children: <Button>{copy.createSubmit}</Button> },
};

/** A destructive primary, which is how the delete dialog closes. */
export const WithDestructivePrimary: Story = {
  args: { children: <Button color="red">{copy.deleteSubmit}</Button> },
};

/** Mid-write: the primary spins and *Cancelar* stays live beside it. */
export const Submitting: Story = {
  args: { children: <Button loading>{copy.createSubmit}</Button> },
};
