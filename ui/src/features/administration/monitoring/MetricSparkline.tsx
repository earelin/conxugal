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
      // Decorative: the tile's value above it is the accessible source of
      // truth, so this never becomes a focus stop or an AT-exposed node.
      <Box inert aria-hidden style={{ height: METRIC_SPARKLINE_HEIGHT, position: 'relative' }}>
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
    // `@mantine/charts`'s Sparkline renders a recharts chart with
    // `accessibilityLayer` on by default, which gives the underlying <svg> a
    // `tabIndex`/`role="application"` that Sparkline exposes no prop to
    // disable. `inert` removes it from both the tab order and the
    // accessibility tree instead, since the sparkline is purely decorative.
    <Box inert aria-hidden>
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
    </Box>
  );
}
