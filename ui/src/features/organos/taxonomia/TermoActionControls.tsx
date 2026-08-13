import { ActionIcon, Button, Group, Tooltip } from '@mantine/core';
import { IconArrowMoveRight, IconPencil, IconTrash } from '@tabler/icons-react';
import type { KeyboardEvent } from 'react';

import { strings } from '../../../shared/lib/strings';

const copy = strings.admin.organos.termo;

/** The writes that act on whichever term is open. */
export interface TermoActionHandlers {
  onRename: () => void;
  onMove: () => void;
  onDelete: () => void;
  /** Files an Órgano into this term — the term's half of the assign dialog. */
  onAssign: () => void;
}

/** The term-content header's row: `Asignar órgano` · `Renomear` · `Mover` · `Eliminar`. */
export function TermoActionButtons({ onRename, onMove, onDelete, onAssign }: TermoActionHandlers) {
  return (
    // Four of these do not fit beside a term name at 360 px, so they wrap onto a
    // second line rather than pushing the card into a horizontal scroll.
    <Group gap="xs" justify="flex-end">
      <Button size="xs" onClick={onAssign}>
        {strings.admin.organos.assign.fromTermo}
      </Button>
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
  /** Named in each label, since the same actions also sit in the header. */
  termoName: string;
}

/**
 * The three term-shape actions on the selected tree row, where they take the
 * place of the count badge. Icon-only to fit a row that already carries a term
 * name at a 360 px viewport, so each one names itself and its term for the
 * accessibility tree.
 *
 * Assign is deliberately not a fourth icon here: three plus a badge is already
 * what fits, and filing an Órgano is reachable from the content header and from
 * every worklist row.
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
