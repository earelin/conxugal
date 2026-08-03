import { ActionIcon, Button, Group, Tooltip } from '@mantine/core';
import { IconArrowMoveRight, IconPencil, IconTrash } from '@tabler/icons-react';
import type { KeyboardEvent } from 'react';

import { strings } from '../../shared/lib/strings';

const copy = strings.admin.organos.termo;

/** The three writes that act on whichever term is open. */
export interface TermoActionHandlers {
  onRename: () => void;
  onMove: () => void;
  onDelete: () => void;
}

/** The term-content header's row: `Renomear` · `Mover` · `Eliminar`. */
export function TermoActionButtons({ onRename, onMove, onDelete }: TermoActionHandlers) {
  return (
    <Group gap="xs" wrap="nowrap">
      <Button variant="default" size="xs" onClick={onRename}>
        {copy.rename}
      </Button>
      <Button variant="default" size="xs" onClick={onMove}>
        {copy.move}
      </Button>
      <Button variant="default" size="xs" c="red" onClick={onDelete}>
        {copy.delete}
      </Button>
    </Group>
  );
}

// Mantine's TreeNode owns Space and the arrows on the row itself — Space
// toggles the branch — and these buttons are ordinary tabbable children of it.
// Without this, Space on a focused icon both opens its dialog and collapses the
// row underneath it.
function keepKeyToSelf(event: KeyboardEvent) {
  if (event.key === ' ' || event.key.startsWith('Arrow')) {
    event.stopPropagation();
  }
}

interface TermoActionIconsProps extends TermoActionHandlers {
  /** Named in each label, since the same three actions also sit in the header. */
  termoName: string;
}

/**
 * The same three actions on the selected tree row, where they take the place of
 * the count badge. Icon-only to fit a row that already carries a term name at a
 * 360 px viewport, so each one names itself and its term for the accessibility
 * tree.
 */
export function TermoActionIcons({ termoName, onRename, onMove, onDelete }: TermoActionIconsProps) {
  const actions = [
    { label: copy.rename, Icon: IconPencil, color: 'indigo', onClick: onRename },
    { label: copy.move, Icon: IconArrowMoveRight, color: 'indigo', onClick: onMove },
    { label: copy.delete, Icon: IconTrash, color: 'red', onClick: onDelete },
  ];

  return (
    <>
      {actions.map(({ label, Icon, color, onClick }) => (
        <Tooltip key={label} label={label}>
          <ActionIcon
            variant="subtle"
            size="sm"
            color={color}
            aria-label={`${label}: ${termoName}`}
            onKeyDown={keepKeyToSelf}
            onClick={(event) => {
              // The row is a tree item: a click on it would otherwise walk up and
              // re-select the very term the dialog is about.
              event.stopPropagation();
              onClick();
            }}
          >
            <Icon size={16} />
          </ActionIcon>
        </Tooltip>
      ))}
    </>
  );
}
