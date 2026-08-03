import { Box, Card, Group, SimpleGrid, Stack, Text } from '@mantine/core';
import { IconLock } from '@tabler/icons-react';
import { useId } from 'react';

import { strings } from '../../../../shared/lib/strings';
import { Field } from './Field';
import { formatPercent } from './metricsFormat';
import type { RuntimeMetrics } from './metricsStream';
import { orNoValue } from './orNoValue';

interface PoolSummary {
  active: number;
  idle: number;
  max: number;
  free: number;
}

function computePoolSummary(pool: RuntimeMetrics['datastorePool']): PoolSummary | null {
  if (pool?.active == null || pool.idle == null || pool.max == null || pool.max <= 0) {
    return null;
  }
  return {
    active: pool.active,
    idle: pool.idle,
    max: pool.max,
    free: Math.max(0, pool.max - pool.active - pool.idle),
  };
}

function PoolBarSegment({ fraction, color }: { fraction: number; color: string }) {
  return (
    <Box
      style={{
        width: `${fraction * 100}%`,
        height: '100%',
        borderRadius: 5,
        background: `var(--mantine-color-${color})`,
      }}
    />
  );
}

function PoolBar({ summary }: { summary: PoolSummary | null }) {
  if (!summary) {
    return (
      <Box style={{ height: 10, borderRadius: 5, background: 'var(--mantine-color-gray-2)' }} />
    );
  }
  return (
    <Group gap={2} wrap="nowrap" style={{ height: 10 }}>
      <PoolBarSegment fraction={summary.active / summary.max} color="indigo-6" />
      <PoolBarSegment fraction={summary.idle / summary.max} color="indigo-2" />
      <PoolBarSegment fraction={summary.free / summary.max} color="gray-2" />
    </Group>
  );
}

function LegendDot({
  color,
  label,
  bordered,
}: {
  color: string;
  label: string;
  bordered?: boolean;
}) {
  return (
    <Group gap={6} wrap="nowrap">
      <Box
        style={{
          width: 10,
          height: 10,
          borderRadius: 3,
          background: `var(--mantine-color-${color})`,
          border: bordered ? '1px solid var(--mantine-color-gray-3)' : undefined,
        }}
      />
      <Text size="xs" c="dimmed">
        {label}
      </Text>
    </Group>
  );
}

export function DatastorePoolCard({ latest }: { latest: RuntimeMetrics | null }) {
  const t = strings.admin.dashboard.metrics;
  const titleId = useId();
  const summary = computePoolSummary(latest?.datastorePool);

  return (
    <Card role="group" aria-labelledby={titleId} withBorder radius="md" padding="lg">
      <Text id={titleId} fw={700} mb="sm">
        {t.datastorePoolCardTitle}
      </Text>
      <Stack gap="xs">
        <Group justify="space-between">
          <Text size="xs" c="dimmed" tt="uppercase">
            {t.poolUsageLabel}
          </Text>
          <Text size="sm">
            {orNoValue(summary, (s) => `${s.active + s.idle} / ${s.max} ${t.poolOpenUnit}`)}
          </Text>
        </Group>
        <PoolBar summary={summary} />
        <Group gap="md">
          <LegendDot
            color="indigo-6"
            label={`${t.poolLegendInUse} ${summary ? summary.active : t.noValue}`}
          />
          <LegendDot
            color="indigo-2"
            label={`${t.poolLegendIdle} ${summary ? summary.idle : t.noValue}`}
          />
          <LegendDot
            color="gray-2"
            bordered
            label={`${t.poolLegendFree} ${summary ? summary.free : t.noValue}`}
          />
        </Group>
        <SimpleGrid cols={2} spacing="lg" mt="xs">
          <Field
            label={t.poolMaxLabel}
            value={orNoValue(summary, (s) => `${s.max} ${t.poolConnectionsUnit}`)}
          />
          <Field
            label={t.poolInUseNowLabel}
            value={orNoValue(summary, (s) => formatPercent(s.active / s.max))}
          />
        </SimpleGrid>
        <Group gap="xs" mt="xs">
          <IconLock size={14} color="var(--mantine-color-gray-6)" />
          <Text size="xs" c="dimmed">
            {t.poolPrivacyNote}
          </Text>
        </Group>
      </Stack>
    </Card>
  );
}
