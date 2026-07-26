import {
  Badge,
  Box,
  Card,
  Group,
  Indicator,
  Progress,
  SimpleGrid,
  Skeleton,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { IconClock, IconHistory, IconLock } from '@tabler/icons-react';
import { useEffect, useState, type ReactNode } from 'react';
import { strings } from '../../../shared/lib/strings';
import { MetricSparkline } from './MetricSparkline';
import {
  deltaOf,
  errorRate,
  formatCount,
  formatDecimal,
  formatMb,
  formatPercent,
  formatSingleMb,
  formatTime,
  formatUptime,
  heapUsedPercent,
  peakOf,
  systemLoadPercent,
} from './metricsFormat';
import {
  METRICS_HISTORY_LIMIT,
  useMetricsStream,
  type MetricsStreamState,
  type RuntimeMetrics,
} from './metricsStream';

interface TileProps {
  state: MetricsStreamState;
  latest: RuntimeMetrics | null;
  history: RuntimeMetrics[];
  variant: 'live' | 'stale';
}

function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Stack gap={0}>
      <Text size="xs" c="dimmed" tt="uppercase">
        {label}
      </Text>
      <Text size="sm">{value}</Text>
    </Stack>
  );
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

function MetricsSectionHeader({
  state,
  lastArrivedAt,
}: {
  state: MetricsStreamState;
  lastArrivedAt: Date | null;
}) {
  const t = strings.admin.dashboard.metrics;
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  useEffect(() => {
    if (state !== 'reconnecting' || !lastArrivedAt) {
      return;
    }
    const tick = () => {
      setElapsedSeconds(Math.max(0, Math.round((Date.now() - lastArrivedAt.getTime()) / 1000)));
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [state, lastArrivedAt]);

  let badgeColor: string;
  let badgeLabel: string;
  let caption: string;

  if (state === 'connecting') {
    badgeColor = 'gray';
    badgeLabel = t.stateConnecting;
    caption = t.awaitingFirstSample;
  } else if (state === 'live') {
    badgeColor = 'green';
    badgeLabel = t.stateLive;
    caption = lastArrivedAt
      ? `${t.lastSampleAtPrefix} ${formatTime(lastArrivedAt)} · ${t.cadenceSuffix}`
      : t.awaitingFirstSample;
  } else {
    badgeColor = 'yellow';
    badgeLabel = t.stateReconnecting;
    caption = lastArrivedAt
      ? `${t.lastKnownSampleAtPrefix} ${formatTime(lastArrivedAt)} · ${t.timeAgoPrefix} ${elapsedSeconds} ${t.timeAgoUnit}`
      : t.awaitingFirstSample;
  }

  const badge = (
    <Badge color={badgeColor} variant="light">
      {badgeLabel}
    </Badge>
  );

  return (
    <Group justify="space-between" wrap="nowrap" align="flex-start">
      <Stack gap={0}>
        <Title order={3}>{t.title}</Title>
        <Text c="dimmed" size="sm">
          {t.subtitle}
        </Text>
      </Stack>
      <Group gap="xs" wrap="nowrap">
        <Text size="xs" c="dimmed">
          {caption}
        </Text>
        {state === 'live' ? (
          <Indicator processing color="green" size={8} offset={4}>
            {badge}
          </Indicator>
        ) : (
          badge
        )}
      </Group>
    </Group>
  );
}

function HeapTile({ state, latest, history, variant }: TileProps) {
  const t = strings.admin.dashboard.metrics;
  const percent = latest ? heapUsedPercent(latest) : null;
  const mbPart =
    latest?.jvm?.heapUsedBytes != null && latest.jvm.heapMaxBytes != null
      ? formatMb(latest.jvm.heapUsedBytes, latest.jvm.heapMaxBytes)
      : null;

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
      value={percent != null ? formatPercent(percent) : t.noValue}
      caption={caption}
      loading={state === 'connecting'}
      dimmed={state === 'reconnecting'}
      sparkline={
        <MetricSparkline
          history={history}
          variant={variant}
          select={(sample) => {
            const p = heapUsedPercent(sample);
            return p != null ? p * 100 : null;
          }}
        />
      }
    />
  );
}

function SystemLoadTile({ state, latest, history, variant }: TileProps) {
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
      value={percent != null ? formatPercent(percent) : t.noValue}
      caption={caption}
      loading={state === 'connecting'}
      dimmed={state === 'reconnecting'}
      sparkline={
        <MetricSparkline
          history={history}
          variant={variant}
          select={(sample) => {
            const p = systemLoadPercent(sample);
            return p != null ? p * 100 : null;
          }}
        />
      }
    />
  );
}

function ThreadsTile({ state, latest, history, variant }: TileProps) {
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
      value={count != null ? formatCount(count) : t.noValue}
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

function HttpTile({ state, latest, history, variant }: TileProps) {
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
      value={count != null ? formatCount(count) : t.noValue}
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

function MemoryGcCard({ latest }: { latest: RuntimeMetrics | null }) {
  const t = strings.admin.dashboard.metrics;
  const jvm = latest?.jvm;
  const heapPercent = latest ? heapUsedPercent(latest) : null;
  const heapMbPart =
    jvm?.heapUsedBytes != null && jvm.heapMaxBytes != null
      ? formatMb(jvm.heapUsedBytes, jvm.heapMaxBytes)
      : null;
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
            value={jvm?.nonHeapUsedBytes != null ? formatSingleMb(jvm.nonHeapUsedBytes) : t.noValue}
          />
          <Field
            label={t.gcCollectionsLabel}
            value={jvm?.gcCollectionCount != null ? formatCount(jvm.gcCollectionCount) : t.noValue}
          />
          <Field
            label={t.gcTimeLabel}
            value={
              jvm?.gcCollectionTimeMillis != null
                ? `${formatDecimal(jvm.gcCollectionTimeMillis / 1000)} s`
                : t.noValue
            }
          />
          <Field
            label={t.sinceStartupLabel}
            value={jvm?.uptimeMillis != null ? formatUptime(jvm.uptimeMillis) : t.noValue}
          />
        </SimpleGrid>
      </Stack>
    </Card>
  );
}

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

function PoolBar({ summary }: { summary: PoolSummary | null }) {
  if (!summary) {
    return (
      <Box style={{ height: 10, borderRadius: 5, background: 'var(--mantine-color-gray-1)' }} />
    );
  }
  const pct = (n: number) => `${(n / summary.max) * 100}%`;
  return (
    <Group gap={2} wrap="nowrap" style={{ height: 10 }}>
      <Box
        style={{
          width: pct(summary.active),
          height: '100%',
          borderRadius: 5,
          background: 'var(--mantine-color-indigo-6)',
        }}
      />
      <Box
        style={{
          width: pct(summary.idle),
          height: '100%',
          borderRadius: 5,
          background: 'var(--mantine-color-indigo-2)',
        }}
      />
      <Box
        style={{
          width: pct(summary.free),
          height: '100%',
          borderRadius: 5,
          background: 'var(--mantine-color-gray-1)',
        }}
      />
    </Group>
  );
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <Group gap={6} wrap="nowrap">
      <Box
        style={{
          width: 10,
          height: 10,
          borderRadius: 3,
          background: `var(--mantine-color-${color})`,
        }}
      />
      <Text size="xs" c="dimmed">
        {label}
      </Text>
    </Group>
  );
}

function DatastorePoolCard({ latest }: { latest: RuntimeMetrics | null }) {
  const t = strings.admin.dashboard.metrics;
  const summary = computePoolSummary(latest?.datastorePool);

  return (
    <Card withBorder radius="md" padding="lg">
      <Text fw={700} mb="sm">
        {t.datastorePoolCardTitle}
      </Text>
      <Stack gap="xs">
        <Group justify="space-between">
          <Text size="xs" c="dimmed" tt="uppercase">
            {t.poolUsageLabel}
          </Text>
          <Text size="sm">
            {summary
              ? `${summary.active + summary.idle} / ${summary.max} ${t.poolOpenUnit}`
              : t.noValue}
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
            color="gray-3"
            label={`${t.poolLegendFree} ${summary ? summary.free : t.noValue}`}
          />
        </Group>
        <SimpleGrid cols={2} spacing="lg" mt="xs">
          <Field
            label={t.poolMaxLabel}
            value={summary ? `${summary.max} ${t.poolConnectionsUnit}` : t.noValue}
          />
          <Field
            label={t.poolInUseNowLabel}
            value={summary ? formatPercent(summary.active / summary.max) : t.noValue}
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

function HttpActivityCard({ latest }: { latest: RuntimeMetrics | null }) {
  const t = strings.admin.dashboard.metrics;
  const http = latest?.http;
  const rate = errorRate(http?.requestCount, http?.errorCount);

  return (
    <Card withBorder radius="md" padding="lg">
      <Text fw={700} mb="sm">
        {t.httpActivityCardTitle}
      </Text>
      <SimpleGrid cols={2} spacing="lg">
        <Field
          label={t.totalRequestsLabel}
          value={http?.requestCount != null ? formatCount(http.requestCount) : t.noValue}
        />
        <Field
          label={t.errorResponsesLabel}
          value={http?.errorCount != null ? formatCount(http.errorCount) : t.noValue}
        />
      </SimpleGrid>
      <Group justify="space-between" mt="sm" align="flex-end">
        <Field
          label={t.errorRateLabel}
          value={rate != null ? `${formatDecimal(rate * 100)} %` : t.noValue}
        />
        {rate != null && (
          <Badge color="green" variant="light">
            {t.errorRateNormalBadge}
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

function MetricsFooterNotes() {
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

export function MetricsPanel() {
  const { state, latest, history, lastArrivedAt } = useMetricsStream();
  const variant: 'live' | 'stale' = state === 'reconnecting' ? 'stale' : 'live';

  return (
    <Stack gap="md">
      <MetricsSectionHeader state={state} lastArrivedAt={lastArrivedAt} />

      <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }}>
        <HeapTile state={state} latest={latest} history={history} variant={variant} />
        <SystemLoadTile state={state} latest={latest} history={history} variant={variant} />
        <ThreadsTile state={state} latest={latest} history={history} variant={variant} />
        <HttpTile state={state} latest={latest} history={history} variant={variant} />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 3 }}>
        <MemoryGcCard latest={latest} />
        <DatastorePoolCard latest={latest} />
        <HttpActivityCard latest={latest} />
      </SimpleGrid>

      <MetricsFooterNotes />
    </Stack>
  );
}
