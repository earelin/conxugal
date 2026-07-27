import { Card, Group, Progress, SimpleGrid, Stack, Text } from '@mantine/core';
import { strings } from '../../../shared/lib/strings';
import { Field } from './Field';
import {
  formatCount,
  formatDecimal,
  formatPercent,
  formatSingleMb,
  formatUptime,
  heapUsageMb,
  heapUsedPercent,
} from './metricsFormat';
import type { RuntimeMetrics } from './metricsStream';
import { orNoValue } from './orNoValue';

export function MemoryGcCard({ latest }: { latest: RuntimeMetrics | null }) {
  const t = strings.admin.dashboard.metrics;
  const jvm = latest?.jvm;
  const heapPercent = latest ? heapUsedPercent(latest) : null;
  const heapMbPart = heapUsageMb(latest);
  const heapValueText =
    heapMbPart != null && heapPercent != null
      ? `${heapMbPart} · ${formatPercent(heapPercent)}`
      : (heapMbPart ?? t.noValue);

  return (
    <Card withBorder radius="md" padding="lg">
      <Text fw={700} mb="sm">
        {t.memoryGcCardTitle}
      </Text>
      <Stack gap="xs">
        <Group justify="space-between">
          <Text size="xs" c="dimmed" tt="uppercase">
            {t.heapInUseLabel}
          </Text>
          <Text size="sm">{heapValueText}</Text>
        </Group>
        <Progress
          value={heapPercent != null ? heapPercent * 100 : 0}
          color="indigo"
          size="sm"
          radius="xl"
        />
        <SimpleGrid cols={2} spacing="lg" mt="xs">
          <Field
            label={t.nonHeapMemoryLabel}
            value={orNoValue(jvm?.nonHeapUsedBytes, formatSingleMb)}
          />
          <Field
            label={t.gcCollectionsLabel}
            value={orNoValue(jvm?.gcCollectionCount, formatCount)}
          />
          <Field
            label={t.gcTimeLabel}
            value={orNoValue(jvm?.gcCollectionTimeMillis, (ms) => `${formatDecimal(ms / 1000)} s`)}
          />
          <Field label={t.sinceStartupLabel} value={orNoValue(jvm?.uptimeMillis, formatUptime)} />
        </SimpleGrid>
      </Stack>
    </Card>
  );
}
