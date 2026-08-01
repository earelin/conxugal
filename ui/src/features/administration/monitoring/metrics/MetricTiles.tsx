import { Box, Card, Skeleton, Text } from '@mantine/core';
import type { ReactNode } from 'react';

import { strings } from '../../../../shared/lib/strings';
import {
  deltaOf,
  formatCount,
  formatPercent,
  fractionToPercent,
  heapUsageMb,
  heapUsedPercent,
  peakOf,
  systemLoadPercent,
} from './metricsFormat';
import { MetricSparkline } from './MetricSparkline';
import {
  METRICS_HISTORY_LIMIT,
  type MetricsStreamState,
  type RuntimeMetrics,
} from './metricsStream';
import { orNoValue } from './orNoValue';

interface TileProps {
  state: MetricsStreamState;
  latest: RuntimeMetrics | null;
  history: RuntimeMetrics[];
}

function buildPeakCaption({
  state,
  history,
  reconnectingCaption,
  prefix,
  peakPrefix,
  select,
  formatPeak,
}: {
  state: MetricsStreamState;
  history: RuntimeMetrics[];
  reconnectingCaption: string;
  prefix: string;
  peakPrefix: string;
  select: (sample: RuntimeMetrics) => number | null;
  formatPeak: (peak: number) => string;
}): string {
  const t = strings.admin.dashboard.metrics;
  // Never actually rendered for state === 'connecting': MetricTile skeletons
  // the caption row in that state, and history is always empty until the
  // first sample flips state to 'live', so there's no connecting-specific
  // branch to compute here.
  if (state === 'reconnecting') {
    return reconnectingCaption;
  }
  if (history.length < METRICS_HISTORY_LIMIT) {
    return `${prefix} · ${history.length}/${METRICS_HISTORY_LIMIT} ${t.samplesUnit}`;
  }
  const peak = peakOf(history, select);
  return peak != null ? `${prefix} · ${peakPrefix} ${formatPeak(peak)}` : prefix;
}

function MetricTile({
  label,
  value,
  caption,
  state,
  sparkline,
}: {
  label: string;
  value: string;
  caption: string;
  state: MetricsStreamState;
  sparkline: ReactNode;
}) {
  const loading = state === 'connecting';
  const dimmed = state === 'reconnecting';

  return (
    <Card withBorder radius="md" padding="lg">
      <Text size="xs" fw={700} c="dimmed" tt="uppercase">
        {label}
      </Text>
      {loading ? (
        // Matches the rendered height of the Text it replaces (20px xl
        // font x 1.65 line-height) so the tile doesn't grow when the first
        // sample arrives and swaps the skeleton for real content.
        <Skeleton height={33} width="60%" radius="sm" mt="xs" />
      ) : (
        <Text size="xl" fw={600} mt="xs" c={dimmed ? 'dimmed' : undefined}>
          {value}
        </Text>
      )}
      {loading ? (
        // Matches the rendered height of the Text it replaces (12px xs
        // font x 1.4 line-height).
        <Skeleton height={17} width="80%" radius="sm" mt={4} />
      ) : (
        <Text size="xs" c="dimmed" mt={4}>
          {caption}
        </Text>
      )}
      <Box mt="sm">{sparkline}</Box>
    </Card>
  );
}

export function HeapTile({ state, latest, history }: TileProps) {
  const t = strings.admin.dashboard.metrics;
  const percent = latest ? heapUsedPercent(latest) : null;
  const mbPart = heapUsageMb(latest);

  // Never actually rendered for state === 'connecting' (see buildPeakCaption).
  let caption: string;
  if (state === 'reconnecting') {
    caption = mbPart ? `${mbPart} · ${t.notUpdating}` : t.notUpdating;
  } else if (history.length < METRICS_HISTORY_LIMIT) {
    const samplesPart = `${history.length}/${METRICS_HISTORY_LIMIT} ${t.samplesUnit}`;
    caption = mbPart ? `${mbPart} · ${samplesPart}` : samplesPart;
  } else {
    const peak = peakOf(history, heapUsedPercent);
    const peakPart = peak != null ? `${t.peaksOfHeapPrefix} ${Math.round(peak * 100)} %` : null;
    caption = [mbPart, peakPart].filter(Boolean).join(' · ') || t.noHistoryYet;
  }

  return (
    <MetricTile
      label={t.heapTileLabel}
      value={orNoValue(percent, formatPercent)}
      caption={caption}
      state={state}
      sparkline={
        <MetricSparkline
          history={history}
          state={state}
          select={(sample) => fractionToPercent(heapUsedPercent(sample))}
        />
      }
    />
  );
}

export function SystemLoadTile({ state, latest, history }: TileProps) {
  const t = strings.admin.dashboard.metrics;
  const percent = latest ? systemLoadPercent(latest) : null;
  const caption = buildPeakCaption({
    state,
    history,
    reconnectingCaption: t.lastKnownLoad,
    prefix: t.recentLoadPrefix,
    peakPrefix: t.maxOfLoadPrefix,
    select: systemLoadPercent,
    formatPeak: (peak) => `${Math.round(peak * 100)} %`,
  });

  return (
    <MetricTile
      label={t.systemLoadTileLabel}
      value={orNoValue(percent, formatPercent)}
      caption={caption}
      state={state}
      sparkline={
        <MetricSparkline
          history={history}
          state={state}
          select={(sample) => fractionToPercent(systemLoadPercent(sample))}
        />
      }
    />
  );
}

export function ThreadsTile({ state, latest, history }: TileProps) {
  const t = strings.admin.dashboard.metrics;
  const count = latest?.jvm?.threadCount ?? null;
  const caption = buildPeakCaption({
    state,
    history,
    reconnectingCaption: t.lastKnownThreads,
    prefix: t.aliveNowPrefix,
    peakPrefix: t.peakOfThreadsPrefix,
    select: (sample) => sample.jvm?.threadCount ?? null,
    formatPeak: (peak) => `${Math.round(peak)}`,
  });

  return (
    <MetricTile
      label={t.threadsTileLabel}
      value={orNoValue(count, formatCount)}
      caption={caption}
      state={state}
      sparkline={
        <MetricSparkline
          history={history}
          state={state}
          select={(sample) => sample.jvm?.threadCount ?? null}
        />
      }
    />
  );
}

export function HttpTile({ state, latest, history }: TileProps) {
  const t = strings.admin.dashboard.metrics;
  const count = latest?.http?.requestCount ?? null;
  const delta = deltaOf(history, (sample) => sample.http?.requestCount ?? null);

  // Never actually rendered for state === 'connecting' (see buildPeakCaption).
  let caption: string;
  if (state === 'reconnecting') {
    caption = t.noNewSamples;
  } else if (delta != null) {
    const sign = delta >= 0 ? '+' : '';
    caption = `${sign}${formatCount(delta)} ${t.sinceLastSamplePrefix}`;
  } else {
    caption = t.noHistoryYet;
  }

  return (
    <MetricTile
      label={t.httpTileLabel}
      value={orNoValue(count, formatCount)}
      caption={caption}
      state={state}
      sparkline={
        <MetricSparkline
          history={history}
          state={state}
          select={(sample) => sample.http?.requestCount ?? null}
        />
      }
    />
  );
}
