import { Badge, Flex, Group, Indicator, Stack, Text, Title } from '@mantine/core';
import { useEffect, useState } from 'react';

import { formatTime } from '../../../../shared/lib/date';
import { strings } from '../../../../shared/lib/strings';
import type { MetricsStreamState } from './metricsStream';

export function MetricsSectionHeader({
  state,
  lastArrivedAt,
}: {
  state: MetricsStreamState;
  lastArrivedAt: Date | null;
}) {
  const t = strings.admin.dashboard.metrics;
  const [now, setNow] = useState(() => Date.now());

  // While the stream is down the caption counts up from the last sample. The
  // clock is held in state rather than read during render, which would make the
  // component impure. It is synced once on the way in — the drop is noticed
  // some seconds after the last sample, so without that the caption would read
  // zero until the first tick — and the sync is deferred by a timeout so it is
  // never a state update made synchronously from an effect.
  useEffect(() => {
    if (state !== 'reconnecting' || !lastArrivedAt) {
      return;
    }
    const sync = () => setNow(Date.now());
    const immediate = setTimeout(sync, 0);
    const id = setInterval(sync, 1000);
    return () => {
      clearTimeout(immediate);
      clearInterval(id);
    };
  }, [state, lastArrivedAt]);

  const elapsedSeconds = lastArrivedAt
    ? Math.max(0, Math.round((now - lastArrivedAt.getTime()) / 1000))
    : 0;

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
    <Flex direction={{ base: 'column', sm: 'row' }} justify="space-between" gap="xs">
      <Stack gap={0}>
        <Title order={3}>{t.title}</Title>
        <Text c="dimmed" size="sm" visibleFrom="sm">
          {t.subtitle}
        </Text>
      </Stack>
      <Group gap="xs" role="status" aria-live="polite">
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
    </Flex>
  );
}
