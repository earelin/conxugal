import { Button, Group } from '@mantine/core';
import type { ReactNode } from 'react';

import { strings } from '../../shared/lib/strings';

interface TermoDialogFooterProps {
  onCancel: () => void;
  /** The dialog's own primary — the half of the pair that differs. */
  children: ReactNode;
}

/** Every term dialog closes the same way, so only its primary is its own. */
export function TermoDialogFooter({ onCancel, children }: TermoDialogFooterProps) {
  return (
    <Group justify="flex-end" mt="sm">
      <Button variant="default" onClick={onCancel}>
        {strings.admin.organos.termo.cancel}
      </Button>
      {children}
    </Group>
  );
}
