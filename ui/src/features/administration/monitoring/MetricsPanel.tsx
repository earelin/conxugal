import { SimpleGrid, Stack } from '@mantine/core';
import { DatastorePoolCard } from './DatastorePoolCard';
import { HttpActivityCard } from './HttpActivityCard';
import { MemoryGcCard } from './MemoryGcCard';
import { HeapTile, HttpTile, SystemLoadTile, ThreadsTile } from './MetricTiles';
import { MetricsFooterNotes } from './MetricsFooterNotes';
import { MetricsSectionHeader } from './MetricsSectionHeader';
import { useMetricsStream } from './metricsStream';

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
