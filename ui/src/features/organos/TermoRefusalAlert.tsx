import { Alert } from '@mantine/core';
import { IconAlertTriangle } from '@tabler/icons-react';

import type { Refusal } from './termoRefusal';

/** Renders a refusal with the title the rule earned, or nothing at all. */
export function TermoRefusalAlert({ refusal }: { refusal: Refusal | null }) {
  if (refusal === null) {
    return null;
  }
  return (
    <Alert color="red" title={refusal.title} icon={<IconAlertTriangle size={18} />}>
      {refusal.message}
    </Alert>
  );
}
