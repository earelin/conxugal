import { Alert, Button, Group } from '@mantine/core';
import { IconAlertCircle, IconRefresh } from '@tabler/icons-react';
import type { ReactNode } from 'react';

import { strings } from '../lib/strings';

interface ErrorAlertProps {
  title?: string;
  children: ReactNode;
  /** Retries the failed read. Omitted where there is nothing to retry. */
  onRetry?: () => void;
}

export function ErrorAlert({ title, children, onRetry }: ErrorAlertProps) {
  return (
    <Alert color="red" icon={<IconAlertCircle size={18} />} title={title}>
      {children}
      {onRetry && (
        <Group mt="sm">
          <Button
            size="xs"
            variant="light"
            color="red"
            leftSection={<IconRefresh size={14} />}
            onClick={onRetry}
          >
            {strings.retry}
          </Button>
        </Group>
      )}
    </Alert>
  );
}
