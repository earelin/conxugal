import type { Meta, StoryObj } from '@storybook/react-vite';
import {
  IconAlertTriangle,
  IconCircleCheck,
  IconInfoCircle,
  IconPlayerPause,
} from '@tabler/icons-react';
import { fn } from 'storybook/test';

import { counted } from '../lib/plural';
import { strings } from '../lib/strings';
import { StatusAlert } from './StatusAlert';

const copy = strings.admin.organos.contratosMenores.run;

/**
 * The colour and the icon are the caller's; what this component fixes is the
 * polite `role="status"` and the dismiss control. The six stories below are the
 * six tones `ImportRunBanner` asks it for, which is every tone in use.
 */
const meta = {
  component: StatusAlert,
  tags: ['autodocs'],
  args: {
    closeLabel: copy.dismiss,
    onDismiss: fn(),
  },
} satisfies Meta<typeof StatusAlert>;

export default meta;

type Story = StoryObj<typeof meta>;

export const InProgress: Story = {
  args: {
    color: 'blue',
    icon: <IconInfoCircle size={18} />,
    title: copy.inProgressTitle,
    children: counted(12, copy.scopeCount),
  },
};

export const Succeeded: Story = {
  args: {
    color: 'green',
    icon: <IconCircleCheck size={18} />,
    title: copy.succeededTitle,
    children: copy.succeededNote,
  },
};

export const Partial: Story = {
  args: {
    color: 'yellow',
    icon: <IconAlertTriangle size={18} />,
    title: copy.partialTitle,
    children: copy.completedOf(10, 12),
  },
};

export const Failed: Story = {
  args: {
    color: 'red',
    icon: <IconAlertTriangle size={18} />,
    title: copy.failedTitle,
    children: copy.failedNote,
  },
};

export const Abandoned: Story = {
  args: {
    color: 'orange',
    icon: <IconPlayerPause size={18} />,
    title: copy.abandonedTitle,
    children: copy.abandonedNote,
  },
};

/**
 * Grey, and the only tone with a rule written down for it: a verdict this build
 * does not recognise is not a failed run, and neither is a refusal — both use
 * this, and neither carries counts.
 */
export const Unknown: Story = {
  args: {
    color: 'gray',
    icon: <IconInfoCircle size={18} />,
    title: copy.unknownTitle,
    children: copy.unknownNote,
  },
};
