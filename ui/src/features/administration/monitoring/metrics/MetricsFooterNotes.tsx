import { Group, Stack, Text } from '@mantine/core';
import { IconHistory, IconLock } from '@tabler/icons-react';

import { strings } from '../../../../shared/lib/strings';
import { METRICS_HISTORY_LIMIT } from './metricsStream';

export function MetricsFooterNotes() {
  const t = strings.admin.dashboard.metrics;
  return (
    <Stack gap={4}>
      <Group gap="xs">
        <IconLock size={14} color="var(--mantine-color-gray-6)" />
        <Text size="xs" c="dimmed">
          {t.privacyNote}
        </Text>
      </Group>
      <Group gap="xs">
        <IconHistory size={14} color="var(--mantine-color-gray-6)" />
        <Text size="xs" c="dimmed">
          {t.historyNotePrefix} {METRICS_HISTORY_LIMIT} {t.historyNoteSuffix}
        </Text>
      </Group>
    </Stack>
  );
}
