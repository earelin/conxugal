import { Sparkline } from '@mantine/charts';
import { Box, Text } from '@mantine/core';
import { strings } from '../../../shared/lib/strings';
import { METRICS_HISTORY_LIMIT, type RuntimeMetrics } from './metricsStream';

export const METRIC_SPARKLINE_HEIGHT = 40;

interface MetricSparklineProps {
  history: RuntimeMetrics[];
  select: (sample: RuntimeMetrics) => number | null;
  variant: 'live' | 'stale';
}

export function MetricSparkline({ history, select, variant }: MetricSparklineProps) {
  if (history.length === 0) {
    return (
      <Box style={{ height: METRIC_SPARKLINE_HEIGHT, position: 'relative' }}>
        <Box
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            borderBottom: '1px solid var(--mantine-color-gray-1)',
          }}
        />
        <Text
          size="xs"
          c="dimmed"
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {strings.admin.dashboard.metrics.noHistoryYet}
        </Text>
      </Box>
    );
  }

  const filled = history.map(select);
  const padding = Array<null>(Math.max(0, METRICS_HISTORY_LIMIT - filled.length)).fill(null);
  const data: (number | null)[] = [...filled, ...padding];

  return (
    <Sparkline
      w="100%"
      h={METRIC_SPARKLINE_HEIGHT}
      data={data}
      curveType="linear"
      strokeWidth={2}
      fillOpacity={0.4}
      connectNulls={false}
      color={variant === 'live' ? 'indigo.6' : 'indigo.2'}
    />
  );
}
