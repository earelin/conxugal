import { Badge, Box, Button, Group, Table, Text } from '@mantine/core';
import { IconFolderMinus, IconFolderPlus, type TablerIcon } from '@tabler/icons-react';
import type { CSSProperties } from 'react';

import { strings } from '../../shared/lib/strings';
import type { Organo } from './organos';

// The two right-hand columns take only the width their content needs, so at a
// 360px viewport the name column absorbs the wrapping instead of them.
const NARROW_COLUMN: CSSProperties = { whiteSpace: 'nowrap', width: '1%' };

// Mantine ellipsises a Badge's label once its column is squeezed, which turns
// ACTIVO into "A…". The state has to stay readable at every width.
const UNTRUNCATED_LABEL = { root: { maxWidth: 'none' }, label: { overflow: 'visible' } };

// The admin tables' column-header treatment.
const COLUMN_HEADER = { tt: 'uppercase', fz: 'xs', c: 'dimmed' } as const;

const copy = strings.admin.organos.assign;

export interface OrganoRowActions {
  onAssign: (organo: Organo) => void;
  /**
   * Absent in the worklist, where a row has no placement to clear. The action
   * exists only inside a term's table.
   */
  onClear?: (organo: Organo) => void;
  /** The Órgano whose clear is in flight, so only its own button spins. */
  clearingId?: string;
}

interface RowActionProps {
  variant: string;
  icon: TablerIcon;
  label: string;
  organo: Organo;
  loading?: boolean;
  onClick: () => void;
}

/**
 * A row's own action. Both labels repeat down the column and again in the panes
 * around it, so each button names its Órgano for the accessibility tree — which
 * is also what lets the label itself drop below `sm`, where a third labelled
 * column would push the table into a horizontal scroll. The icon and the
 * accessible name carry it there; nothing is hidden from a screen reader.
 */
function RowAction({ variant, icon: Icon, label, organo, loading, onClick }: RowActionProps) {
  return (
    <Button
      variant={variant}
      size="compact-sm"
      aria-label={`${label}: ${organo.name}`}
      loading={loading}
      onClick={onClick}
    >
      <Icon size={14} aria-hidden />
      <Box component="span" ml={6} visibleFrom="sm">
        {label}
      </Box>
    </Button>
  );
}

interface OrganoRowProps {
  organo: Organo;
  actions: OrganoRowActions;
}

function OrganoRow({ organo, actions }: OrganoRowProps) {
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
      <Table.Td ta="right" style={NARROW_COLUMN}>
        <Group gap="xs" justify="flex-end" wrap="nowrap">
          {actions.onClear === undefined ? (
            <RowAction
              variant="light"
              icon={IconFolderPlus}
              label={copy.fromWorklist}
              organo={organo}
              onClick={() => actions.onAssign(organo)}
            />
          ) : (
            <RowAction
              variant="default"
              icon={IconFolderMinus}
              label={copy.clear}
              organo={organo}
              loading={actions.clearingId === organo.id}
              onClick={() => actions.onClear?.(organo)}
            />
          )}
        </Group>
      </Table.Td>
    </Table.Tr>
  );
}

interface OrganosTableProps {
  organos: Organo[];
  emptyMessage: string;
  label: string;
  actions: OrganoRowActions;
}

export function OrganosTable({ organos, emptyMessage, label, actions }: OrganosTableProps) {
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
          <Table.Th {...COLUMN_HEADER}>{strings.admin.organos.columnOrgano}</Table.Th>
          <Table.Th {...COLUMN_HEADER} style={NARROW_COLUMN}>
            {strings.admin.organos.columnState}
          </Table.Th>
          <Table.Th {...COLUMN_HEADER} ta="right" style={NARROW_COLUMN}>
            {strings.admin.organos.columnActions}
          </Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {organos.map((organo) => (
          <OrganoRow key={organo.id} organo={organo} actions={actions} />
        ))}
      </Table.Tbody>
    </Table>
  );
}
