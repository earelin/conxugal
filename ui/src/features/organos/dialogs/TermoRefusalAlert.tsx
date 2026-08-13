import { Alert, Button } from '@mantine/core';
import { IconAlertTriangle } from '@tabler/icons-react';

import { strings } from '../../../shared/lib/strings';
import type { Refusal } from '../taxonomia/termoRefusal';

interface TermoRefusalAlertProps {
  refusal: Refusal | null;
  /**
   * Offered by the refusals that are about another administrator's edit rather
   * than about this form: re-reading the section is the only thing that can make
   * the choice possible, so the way out sits in the alert that explains it.
   */
  onRefresh?: () => void;
}

/** Renders a refusal with the title the rule earned, or nothing at all. */
export function TermoRefusalAlert({ refusal, onRefresh }: TermoRefusalAlertProps) {
  if (refusal === null) {
    return null;
  }
  return (
    <Alert color="red" title={refusal.title} icon={<IconAlertTriangle size={18} />}>
      {refusal.message}
      {onRefresh && (
        <Button variant="light" color="red" size="xs" mt="sm" display="block" onClick={onRefresh}>
          {strings.admin.organos.assign.refresh}
        </Button>
      )}
    </Alert>
  );
}
