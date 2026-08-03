import { ActionIcon, Button, Group, Tooltip } from '@mantine/core';
import { IconArrowMoveRight, IconPencil, IconTrash } from '@tabler/icons-react';

import { strings } from '../../shared/lib/strings';

const copy = strings.admin.organos.termo;

/** The three writes that act on whichever term is open. */
export interface TermoActionHandlers {
  onRename: () => void;
  onMove: () => void;
  onDelete: () => void;
}

const ACTIONS = [
  { key: 'rename', label: copy.rename, Icon: IconPencil, color: undefined },
  { key: 'move', label: copy.move, Icon: IconArrowMoveRight, color: undefined },
  { key: 'delete', label: copy.delete, Icon: IconTrash, color: 'red' },
] as const;

function handlerFor(handlers: TermoActionHandlers, key: (typeof ACTIONS)[number]['key']) {
  return { rename: handlers.onRename, move: handlers.onMove, delete: handlers.onDelete }[key];
}

/** The term-content header's row: `Renomear` · `Mover` · `Eliminar`. */
export function TermoActionButtons(handlers: TermoActionHandlers) {
  return (
    <Group gap="xs" wrap="nowrap">
      {ACTIONS.map(({ key, label, color }) => (
        <Button key={key} variant="default" size="xs" c={color} onClick={handlerFor(handlers, key)}>
          {label}
        </Button>
      ))}
    </Group>
  );
}

/**
 * The same three actions on the selected tree row, where they take the place of
 * the count badge. Icon-only to fit a row that already carries a term name at a
 * 360 px viewport, so each one names itself for the accessibility tree.
 */
export function TermoActionIcons(handlers: TermoActionHandlers) {
  return (
    <>
      {ACTIONS.map(({ key, label, Icon, color }) => (
        <Tooltip key={key} label={label}>
          <ActionIcon
            variant="subtle"
            size="sm"
            color={color ?? 'indigo'}
            aria-label={label}
            onClick={(event) => {
              // The row is a tree item: a click on it would otherwise walk up and
              // re-select the very term the dialog is about.
              event.stopPropagation();
              handlerFor(handlers, key)();
            }}
          >
            <Icon size={15} />
          </ActionIcon>
        </Tooltip>
      ))}
    </>
  );
}
