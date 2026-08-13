import { Badge, Card, Group, SimpleGrid, Text } from '@mantine/core';
import { IconClock } from '@tabler/icons-react';
import { useId } from 'react';

import { formatCount } from '../../../../shared/lib/number';
import { strings } from '../../../../shared/lib/strings';
import { Field } from './Field';
import { errorRate, errorRateSeverity, formatDecimal } from './metricsFormat';
import type { RuntimeMetrics } from './metricsStream';
import { orNoValue } from './orNoValue';

const ERROR_RATE_BADGE = {
  normal: { color: 'green', labelKey: 'errorRateNormalBadge' },
  elevated: { color: 'yellow', labelKey: 'errorRateElevatedBadge' },
  high: { color: 'red', labelKey: 'errorRateHighBadge' },
} as const;

export function HttpActivityCard({ latest }: { latest: RuntimeMetrics | null }) {
  const t = strings.admin.dashboard.metrics;
  const titleId = useId();
  const http = latest?.http;
  const rate = errorRate(http?.requestCount, http?.errorCount);
  // Classify severity on the same value the user actually sees (rounded to
  // the 2 decimal places `formatDecimal` displays for the percentage), so
  // two rates that render identically never get different-coloured badges.
  const displayedRate = rate != null ? Math.round(rate * 10000) / 10000 : null;
  const badge = displayedRate != null ? ERROR_RATE_BADGE[errorRateSeverity(displayedRate)] : null;

  return (
    <Card role="group" aria-labelledby={titleId} withBorder radius="md" padding="lg">
      <Text id={titleId} fw={700} mb="sm">
        {t.httpActivityCardTitle}
      </Text>
      <SimpleGrid cols={2} spacing="lg">
        <Field label={t.totalRequestsLabel} value={orNoValue(http?.requestCount, formatCount)} />
        <Field label={t.errorResponsesLabel} value={orNoValue(http?.errorCount, formatCount)} />
      </SimpleGrid>
      <Group justify="space-between" mt="sm" align="flex-end">
        <Field
          label={t.errorRateLabel}
          value={orNoValue(displayedRate, (r) => `${formatDecimal(r * 100)} %`)}
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
