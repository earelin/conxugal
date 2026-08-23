import type { Meta, StoryObj } from '@storybook/react-vite';

import type { ContratoMenor } from './contracts';
import { ContratosMenoresTable } from './ContratosMenoresTable';

function contract(overrides: Partial<ContratoMenor> & { sourceId: number }): ContratoMenor {
  return {
    publicationDate: '2026-05-14',
    obxecto: 'Subministración de material funxible de laboratorio',
    amount: 12_480.55,
    duration: '3 meses',
    awardee: { name: 'Subministracións Galegas S.L.', fiscalId: 'B15123456' },
    sourceUrl: 'https://www.contratosdegalicia.gal/resultado.jsp?N=1042117',
    ...overrides,
  };
}

const CONTRACTS: ContratoMenor[] = [
  contract({ sourceId: 1_042_117 }),
  contract({
    sourceId: 1_042_206,
    publicationDate: '2026-05-02',
    obxecto: 'Servizo de mantemento dos equipos de climatización do edificio administrativo',
    amount: 8_990,
    duration: 'Ata o remate da anualidade orzamentaria, prorrogable por acordo das partes',
    awardee: { name: 'Climatizacións do Noroeste, S.L.U.', fiscalId: 'B36998877' },
  }),
  // The two fields the source does not always publish, and the only two this
  // list has an absent form for.
  contract({
    sourceId: 1_041_884,
    publicationDate: '2026-04-27',
    obxecto: null,
    duration: null,
    amount: 3_200,
    awardee: { name: 'Xoán Rodríguez Vilar', fiscalId: '35678912P' },
  }),
  contract({
    sourceId: 1_041_502,
    publicationDate: '2026-04-11',
    obxecto: 'Redacción do proxecto de rehabilitación enerxética',
    amount: 14_999.99,
    duration: '45 días naturais',
    awardee: {
      name: 'Estudo de Arquitectura e Enxeñaría do Atlántico, Sociedade Cooperativa Galega',
      fiscalId: 'F70112233',
    },
  }),
];

/**
 * Every attribute the system holds for a contrato menor, on one row: the family
 * has no detail view to click into, which is why the row is wide and why the
 * table scrolls sideways inside its own focusable region below 720 px.
 *
 * Worth browsing at a narrow viewport — the sideways scroll and the duration
 * caveat sitting outside it are the two things the width decides.
 */
const meta = {
  component: ContratosMenoresTable,
  tags: ['autodocs'],
  args: { contracts: CONTRACTS },
} satisfies Meta<typeof ContratosMenoresTable>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Contracts: Story = {};

/** The two absent-capable fields, both absent. */
export const WithoutObxectoOrDuration: Story = {
  args: { contracts: [CONTRACTS[2]] },
};

/**
 * The header, the caveat and the scrolling region all still draw. The section
 * above decides whether an empty selection is worth showing at all.
 */
export const NoContracts: Story = {
  args: { contracts: [] },
};
