import { Badge, Card, Group, SimpleGrid, Text } from '@mantine/core';
import { IconClock } from '@tabler/icons-react';
import { strings } from '../../../../shared/lib/strings';
import { Field } from './Field';
import { errorRate, errorRateSeverity, formatCount, formatDecimal } from './metricsFormat';
import type { RuntimeMetrics } from './metricsStream';
import { orNoValue } from './orNoValue';

const ERROR_RATE_BADGE = {
  normal: { color: 'green', labelKey: 'errorRateNormalBadge' },
  elevated: { color: 'yellow', labelKey: 'errorRateElevatedBadge' },
  high: { color: 'red', labelKey: 'errorRateHighBadge' },
} as const;

export function HttpActivityCard({ latest }: { latest: RuntimeMetrics | null }) {
  const t = strings.admin.dashboard.metrics;
  const http = latest?.http;
  const rate = errorRate(http?.requestCount, http?.errorCount);
  const badge = rate != null ? ERROR_RATE_BADGE[errorRateSeverity(rate)] : null;

  return (
    <Card withBorder radius="md" padding="lg">
      <Text fw={700} mb="sm">
        {t.httpActivityCardTitle}
      </Text>
      <SimpleGrid cols={2} spacing="lg">
        <Field label={t.totalRequestsLabel} value={orNoValue(http?.requestCount, formatCount)} />
        <Field label={t.errorResponsesLabel} value={orNoValue(http?.errorCount, formatCount)} />
      </SimpleGrid>
      <Group justify="space-between" mt="sm" align="flex-end">
        <Field
          label={t.errorRateLabel}
          value={orNoValue(rate, (r) => `${formatDecimal(r * 100)} %`)}
        />
        {badge && (
          <Badge color={badge.color} variant="light">
            {t[badge.labelKey]}
          </Badge>
        )}
      </Group>
      <Group gap="xs" mt="sm">
        <IconClock size={14} color="var(--mantine-color-gray-6)" />
        <Text size="xs" c="dimmed">
          {t.httpAccumulatedNote}
        </Text>
      </Group>
    </Card>
  );
}
