import { SimpleGrid, Stack } from '@mantine/core';

import { DatastorePoolCard } from './DatastorePoolCard';
import { HttpActivityCard } from './HttpActivityCard';
import { MemoryGcCard } from './MemoryGcCard';
import { MetricsFooterNotes } from './MetricsFooterNotes';
import { MetricsSectionHeader } from './MetricsSectionHeader';
import { useMetricsStream } from './metricsStream';
import { HeapTile, HttpTile, SystemLoadTile, ThreadsTile } from './MetricTiles';

export function MetricsPanel() {
  const { state, latest, history, lastArrivedAt } = useMetricsStream();

  return (
    <Stack gap="md">
      <MetricsSectionHeader state={state} lastArrivedAt={lastArrivedAt} />

      <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }}>
        <HeapTile state={state} latest={latest} history={history} />
        <SystemLoadTile state={state} latest={latest} history={history} />
        <ThreadsTile state={state} latest={latest} history={history} />
        <HttpTile state={state} latest={latest} history={history} />
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
