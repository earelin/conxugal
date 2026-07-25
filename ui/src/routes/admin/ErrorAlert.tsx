import { Alert } from '@mantine/core';
import { IconAlertCircle } from '@tabler/icons-react';
import type { ReactNode } from 'react';

export function ErrorAlert({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <Alert color="red" icon={<IconAlertCircle size={18} />} title={title}>
      {children}
    </Alert>
  );
}
