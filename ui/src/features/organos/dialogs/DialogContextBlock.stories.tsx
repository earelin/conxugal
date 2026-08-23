import type { Meta, StoryObj } from '@storybook/react-vite';

import { strings } from '../../../shared/lib/strings';
import { DialogContextBlock } from './DialogContextBlock';

const copy = strings.admin.organos.assign;

/**
 * The half of a dialog's pair that is already decided: stated, and not
 * editable. Every term dialog opens with one of these.
 */
const meta = {
  component: DialogContextBlock,
  tags: ['autodocs'],
} satisfies Meta<typeof DialogContextBlock>;

export default meta;

type Story = StoryObj<typeof meta>;

/** From a worklist row: the Órgano is settled, the term is the question. */
export const OrganoWithPlacement: Story = {
  args: {
    label: copy.organoLabel,
    name: 'Servizo Galego de Saúde',
    note: copy.currently('Consellerías › Consellería de Sanidade'),
  },
};

/** From a term header: the term is settled, so there is nothing to add under it. */
export const TermoWithoutNote: Story = {
  args: {
    label: copy.termoLabel,
    name: 'Consellerías › Consellería de Educación › Axencias e entidades instrumentais',
  },
};
