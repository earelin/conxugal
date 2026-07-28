import { Badge, Flex, Group, Indicator, Stack, Text, Title } from '@mantine/core';
import { useEffect, useState } from 'react';
import { strings } from '../../../../shared/lib/strings';
import { formatTime } from './metricsFormat';
import type { MetricsStreamState } from './metricsStream';

export function MetricsSectionHeader({
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
