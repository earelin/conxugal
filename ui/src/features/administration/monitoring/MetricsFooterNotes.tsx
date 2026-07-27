import { Group, Stack, Text } from '@mantine/core';
import { IconHistory, IconLock } from '@tabler/icons-react';
import { strings } from '../../../shared/lib/strings';

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
          {t.historyNote}
        </Text>
      </Group>
    </Stack>
  );
}
