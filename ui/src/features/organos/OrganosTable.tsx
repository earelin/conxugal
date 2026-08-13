import { Badge, Box, Button, Group, Table, Text } from '@mantine/core';
import { IconFolderMinus, IconFolderPlus, type TablerIcon } from '@tabler/icons-react';
import type { CSSProperties } from 'react';

import { strings } from '../../shared/lib/strings';
import { UNTRUNCATED_LABEL } from '../../shared/ui/badge';
import { type ImportMarkActions, ImportMarkCell } from './ImportMarkCell';
import type { Organo } from './organos';

// The three right-hand columns take only the width their content needs, so at a
// 360px viewport the name column absorbs the wrapping instead of them.
const NARROW_COLUMN: CSSProperties = { whiteSpace: 'nowrap', width: '1%' };

// The same, minus the nowrap: CONTRATOS MENORES is far too long a header to hold
// on one line at 360px, and forcing it would push the table into a page scroll.
const MARK_COLUMN: CSSProperties = { width: '1%' };

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
  /** The import mark, which every row carries wherever the table is rendered. */
  mark: ImportMarkActions;
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
      <Table.Td style={{ ...dimmed, ...MARK_COLUMN }}>
        <ImportMarkCell organo={organo} actions={actions.mark} />
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
    // A fourth column spends the width the three-column table had left over: at
    // 360 px the mark, the state badge and the row action together need more
    // than the card can give, so the table scrolls inside its own region rather
    // than dragging the whole page sideways. Above `sm` nothing scrolls.
    <Table.ScrollContainer minWidth={320} type="native">
      <Table aria-label={label} verticalSpacing="sm">
        <Table.Thead>
          <Table.Tr>
            <Table.Th {...COLUMN_HEADER}>{strings.admin.organos.columnOrgano}</Table.Th>
            <Table.Th {...COLUMN_HEADER} style={NARROW_COLUMN}>
              {strings.admin.organos.columnState}
            </Table.Th>
            <Table.Th {...COLUMN_HEADER} style={MARK_COLUMN}>
              {strings.admin.organos.columnContratosMenores}
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
    </Table.ScrollContainer>
  );
}
