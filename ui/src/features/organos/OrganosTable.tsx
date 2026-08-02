import { Badge, Table, Text } from '@mantine/core';
import type { CSSProperties } from 'react';

import { strings } from '../../shared/lib/strings';
import type { Organo } from './organos';

// The two right-hand columns take only the width their content needs, so at a
// 360px viewport the name column absorbs the wrapping instead of them.
const NARROW_COLUMN: CSSProperties = { whiteSpace: 'nowrap', width: '1%' };

// Mantine ellipsises a Badge's label once its column is squeezed, which turns
// ACTIVO into "A…". The state has to stay readable at every width.
const UNTRUNCATED_LABEL = { root: { maxWidth: 'none' }, label: { overflow: 'visible' } };

function OrganoRow({ organo }: { organo: Organo }) {
  // Inactive Órganos are dimmed rather than hidden: they stay part of the
  // catalogue and stay classifiable.
  const dimmed = organo.active ? undefined : { opacity: 0.6 };

  return (
    <Table.Tr>
      <Table.Td style={dimmed}>{organo.name}</Table.Td>
      <Table.Td style={{ ...dimmed, ...NARROW_COLUMN }}>
        <Badge variant="light" color={organo.active ? 'green' : 'gray'} styles={UNTRUNCATED_LABEL}>
          {organo.active ? strings.admin.organos.stateActive : strings.admin.organos.stateInactive}
        </Badge>
      </Table.Td>
      {/* Row action slot: assign and clear land here. */}
      <Table.Td ta="right" style={NARROW_COLUMN} />
    </Table.Tr>
  );
}

interface OrganosTableProps {
  organos: Organo[];
  emptyMessage: string;
  label: string;
}

export function OrganosTable({ organos, emptyMessage, label }: OrganosTableProps) {
  if (organos.length === 0) {
    return (
      <Text c="dimmed" size="sm">
        {emptyMessage}
      </Text>
    );
  }

  return (
    <Table aria-label={label} verticalSpacing="sm">
      <Table.Thead>
        <Table.Tr>
          <Table.Th tt="uppercase" fz="xs" c="dimmed">
            {strings.admin.organos.columnOrgano}
          </Table.Th>
          <Table.Th tt="uppercase" fz="xs" c="dimmed" style={NARROW_COLUMN}>
            {strings.admin.organos.columnState}
          </Table.Th>
          <Table.Th tt="uppercase" fz="xs" c="dimmed" ta="right" style={NARROW_COLUMN}>
            {strings.admin.organos.columnActions}
          </Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {organos.map((organo) => (
          <OrganoRow key={organo.id} organo={organo} />
        ))}
      </Table.Tbody>
    </Table>
  );
}
