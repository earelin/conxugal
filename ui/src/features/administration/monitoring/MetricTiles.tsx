import { Box, Card, Skeleton, Text } from '@mantine/core';
import type { ReactNode } from 'react';
import { strings } from '../../../shared/lib/strings';
import { MetricSparkline } from './MetricSparkline';
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
import {
  METRICS_HISTORY_LIMIT,
  type MetricsStreamState,
  type RuntimeMetrics,
} from './metricsStream';
import { orNoValue } from './orNoValue';

export interface TileProps {
  state: MetricsStreamState;
  latest: RuntimeMetrics | null;
  history: RuntimeMetrics[];
  variant: 'live' | 'stale';
}

function MetricTile({
  label,
  value,
  caption,
  loading,
  dimmed,
  sparkline,
}: {
  label: string;
  value: string;
  caption: string;
  loading: boolean;
  dimmed: boolean;
  sparkline: ReactNode;
}) {
  return (
    <Card withBorder radius="md" padding="lg">
      <Text size="xs" fw={700} c="dimmed" tt="uppercase">
        {label}
      </Text>
      {loading ? (
        <Skeleton height={28} width="60%" radius="sm" mt="xs" />
      ) : (
        <Text size="xl" fw={600} mt="xs" c={dimmed ? 'dimmed' : undefined}>
          {value}
        </Text>
      )}
      {loading ? (
        <Skeleton height={12} width="80%" radius="sm" mt={8} />
      ) : (
        <Text size="xs" c="dimmed" mt={4}>
          {caption}
        </Text>
      )}
      <Box mt="sm">{sparkline}</Box>
    </Card>
  );
}

export function HeapTile({ state, latest, history, variant }: TileProps) {
  const t = strings.admin.dashboard.metrics;
  const percent = latest ? heapUsedPercent(latest) : null;
  const mbPart = heapUsageMb(latest);

  let caption: string;
  if (state === 'connecting') {
    caption = t.noHistoryYet;
  } else if (state === 'reconnecting') {
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
      loading={state === 'connecting'}
      dimmed={state === 'reconnecting'}
      sparkline={
        <MetricSparkline
          history={history}
          variant={variant}
          select={(sample) => fractionToPercent(heapUsedPercent(sample))}
        />
      }
    />
  );
}

export function SystemLoadTile({ state, latest, history, variant }: TileProps) {
  const t = strings.admin.dashboard.metrics;
  const percent = latest ? systemLoadPercent(latest) : null;

  let caption: string;
  if (state === 'connecting') {
    caption = t.noHistoryYet;
  } else if (state === 'reconnecting') {
    caption = t.lastKnownLoad;
  } else if (history.length < METRICS_HISTORY_LIMIT) {
    caption = `${t.recentLoadPrefix} · ${history.length}/${METRICS_HISTORY_LIMIT} ${t.samplesUnit}`;
  } else {
    const peak = peakOf(history, systemLoadPercent);
    caption =
      peak != null
        ? `${t.recentLoadPrefix} · ${t.maxOfLoadPrefix} ${Math.round(peak * 100)} %`
        : t.recentLoadPrefix;
  }

  return (
    <MetricTile
      label={t.systemLoadTileLabel}
      value={orNoValue(percent, formatPercent)}
      caption={caption}
      loading={state === 'connecting'}
      dimmed={state === 'reconnecting'}
      sparkline={
        <MetricSparkline
          history={history}
          variant={variant}
          select={(sample) => fractionToPercent(systemLoadPercent(sample))}
        />
      }
    />
  );
}

export function ThreadsTile({ state, latest, history, variant }: TileProps) {
  const t = strings.admin.dashboard.metrics;
  const count = latest?.jvm?.threadCount ?? null;

  let caption: string;
  if (state === 'connecting') {
    caption = t.noHistoryYet;
  } else if (state === 'reconnecting') {
    caption = t.lastKnownThreads;
  } else if (history.length < METRICS_HISTORY_LIMIT) {
    caption = `${t.aliveNowPrefix} · ${history.length}/${METRICS_HISTORY_LIMIT} ${t.samplesUnit}`;
  } else {
    const peak = peakOf(history, (sample) => sample.jvm?.threadCount ?? null);
    caption =
      peak != null
        ? `${t.aliveNowPrefix} · ${t.peakOfThreadsPrefix} ${Math.round(peak)}`
        : t.aliveNowPrefix;
  }

  return (
    <MetricTile
      label={t.threadsTileLabel}
      value={orNoValue(count, formatCount)}
      caption={caption}
      loading={state === 'connecting'}
      dimmed={state === 'reconnecting'}
      sparkline={
        <MetricSparkline
          history={history}
          variant={variant}
          select={(sample) => sample.jvm?.threadCount ?? null}
        />
      }
    />
  );
}

export function HttpTile({ state, latest, history, variant }: TileProps) {
  const t = strings.admin.dashboard.metrics;
  const count = latest?.http?.requestCount ?? null;
  const delta = deltaOf(history, (sample) => sample.http?.requestCount ?? null);

  let caption: string;
  if (state === 'connecting') {
    caption = t.noHistoryYet;
  } else if (state === 'reconnecting') {
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
      loading={state === 'connecting'}
      dimmed={state === 'reconnecting'}
      sparkline={
        <MetricSparkline
          history={history}
          variant={variant}
          select={(sample) => sample.http?.requestCount ?? null}
        />
      }
    />
  );
}
