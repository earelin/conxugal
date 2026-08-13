import { Button, Group, List, type MantineColor, Stack, Text } from '@mantine/core';
import {
  IconAlertTriangle,
  IconCircleCheck,
  IconInfoCircle,
  IconPlayerPause,
  IconRefresh,
} from '@tabler/icons-react';
import type { UseQueryResult } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';

import { formatHourMinute } from '../../shared/lib/date';
import { strings } from '../../shared/lib/strings';
import { StatusAlert } from '../../shared/ui/StatusAlert';
import type { ImportRun } from './contratosMenores';
import type { ImportAttempt } from './importAttempt';
import { describeRefusal, describeRun, runReadError, type RunTone } from './importRunOutcome';
import type { Organo } from './organos';

const copy = strings.admin.organos.contratosMenores;

const TONE: Record<RunTone, { color: MantineColor; icon: ReactNode }> = {
  progress: { color: 'blue', icon: <IconInfoCircle size={18} /> },
  success: { color: 'green', icon: <IconCircleCheck size={18} /> },
  partial: { color: 'yellow', icon: <IconAlertTriangle size={18} /> },
  // The one red state: a run in which no Órgano could be imported.
  failure: { color: 'red', icon: <IconAlertTriangle size={18} /> },
  abandoned: { color: 'orange', icon: <IconPlayerPause size={18} /> },
  unknown: { color: 'gray', icon: <IconInfoCircle size={18} /> },
};

/** How often the elapsed caption is redrawn; nothing else here moves on its own. */
const ELAPSED_TICK_MS = 30_000;

const MINUTES_PER_HOUR = 60;

/**
 * How long ago the run was last read, so a reader can tell fresh from stale
 * without the banner polling on their behalf.
 */
function CheckedAgo({ readAt }: { readAt: number }) {
  const [now, setNow] = useState(() => Date.now());

  // Held in state rather than read during render, which would make the component
  // impure. This is the only thing in the banner that advances by itself, and
  // what it advances is the caption, never the data behind it. A fresh read is
  // not synced here: a `readAt` ahead of the last tick simply clamps to zero,
  // which is what "agora mesmo" means anyway.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ELAPSED_TICK_MS);
    return () => clearInterval(id);
  }, []);

  const minutes = Math.floor(Math.max(0, now - readAt) / 60_000);

  // A multi-day run left on screen would otherwise reach "hai 4 137 min", which
  // is a number to decode rather than a freshness a reader can feel.
  let elapsed: string;
  if (minutes === 0) {
    elapsed = copy.run.checkedJustNow;
  } else if (minutes < MINUTES_PER_HOUR) {
    elapsed = `${copy.run.checkedAgoPrefix} ${minutes} ${copy.run.checkedAgoUnit}`;
  } else {
    const hours = Math.floor(minutes / MINUTES_PER_HOUR);
    elapsed = `${copy.run.checkedAgoPrefix} ${hours} ${copy.run.checkedAgoHourUnit}`;
  }

  return (
    <Text size="xs" c="dimmed">
      {elapsed}
    </Text>
  );
}

function nameOf(catalogue: Organo[], organoId: string): string {
  return catalogue.find((organo) => organo.id === organoId)?.name ?? organoId;
}

interface RefusalBannerProps {
  attempt: Extract<ImportAttempt, { kind: 'refusal' }>;
  onRetry: () => void;
  retrying: boolean;
  onDismiss: () => void;
}

function RefusalBanner({ attempt, onRetry, retrying, onDismiss }: RefusalBannerProps) {
  const report = describeRefusal(attempt.refusal, attempt.organo?.name ?? null);

  // Grey rather than red, and carrying no counts: a refusal is an outcome, and
  // one of these even wrote the mark it was asked for.
  return (
    <StatusAlert
      color="gray"
      icon={<IconInfoCircle size={18} />}
      title={report.title}
      closeLabel={copy.run.dismiss}
      onDismiss={onDismiss}
    >
      <Stack gap="xs">
        <Text size="sm">{report.message}</Text>
        <Text size="xs" c="dimmed">
          {report.note}
        </Text>
        <Group gap="sm">
          <Text size="xs" c="dimmed">
            {`${copy.refusal.refusedAtPrefix} ${formatHourMinute(attempt.refusedAt)}`}
          </Text>
          {report.retryable && (
            <Button size="xs" variant="default" loading={retrying} onClick={onRetry}>
              {strings.retry}
            </Button>
          )}
        </Group>
      </Stack>
    </StatusAlert>
  );
}

interface ImportRunBannerProps {
  attempt: ImportAttempt | null;
  /**
   * The run read, owned by the toolbar: whether one is in progress decides
   * whether either trigger may be pressed, so both cannot read it separately.
   */
  run: UseQueryResult<ImportRun>;
  /** The catalogue, so a covered Órgano can be named rather than identified. */
  catalogue: Organo[];
  /** Re-issues whatever was refused; the guard is the only refusal worth it. */
  onRetry: () => void;
  /** Whether that re-issued request is in flight, so it cannot be sent twice. */
  retrying: boolean;
  onDismiss: () => void;
  /** Re-reads the run and the catalogue a settled run has changed. */
  onRefresh: () => void;
}

/**
 * The outcome of the one import this session asked for, beside the catalogue
 * import's own feedback.
 *
 * Nothing here is a progress indicator: no percentage, no *n de m*, no bar.
 * `Actualizar` is the only thing that re-reads, and watching a live run belongs
 * to a later specification.
 */
export function ImportRunBanner({
  attempt,
  run,
  catalogue,
  onRetry,
  retrying,
  onDismiss,
  onRefresh,
}: ImportRunBannerProps) {
  if (attempt === null) {
    return null;
  }

  if (attempt.kind === 'refusal') {
    return (
      <RefusalBanner
        attempt={attempt}
        onRetry={onRetry}
        retrying={retrying}
        onDismiss={onDismiss}
      />
    );
  }

  // Dismissible like every other report here: a run that cannot be read is not
  // a section that failed to load, and leaving an alert nothing can clear on
  // screen is what a 404 on a run identity would otherwise do for good.
  if (run.isError) {
    return (
      <StatusAlert
        color="red"
        icon={<IconAlertTriangle size={18} />}
        title={copy.run.errorTitle}
        closeLabel={copy.run.dismiss}
        onDismiss={onDismiss}
      >
        <Stack gap="xs">
          <Text size="sm">{runReadError(run.error)}</Text>
          <Group>
            <Button
              size="xs"
              variant="default"
              leftSection={<IconRefresh size={14} />}
              loading={run.isFetching}
              onClick={onRefresh}
            >
              {strings.retry}
            </Button>
          </Group>
        </Stack>
      </StatusAlert>
    );
  }

  if (!run.data) {
    return null;
  }

  const report = describeRun(run.data, (organoId) => nameOf(catalogue, organoId));
  const tone = TONE[report.tone];

  return (
    <StatusAlert
      color={tone.color}
      icon={tone.icon}
      title={report.title}
      closeLabel={copy.run.dismiss}
      onDismiss={onDismiss}
    >
      <Stack gap="xs">
        <Text size="sm">{report.counts}</Text>

        {report.failuresTitle && (
          <Stack gap={2}>
            <Text size="sm" fw={600}>
              {report.failuresTitle}
            </Text>
            <List size="sm">
              {report.failures.map((failure) => (
                // Keyed on the identity the run read carries: two Órganos can
                // share a name, and two failures the same reason.
                <List.Item key={failure.organoId}>{failure.line}</List.Item>
              ))}
            </List>
          </Stack>
        )}

        {report.note && (
          <Text size="xs" c="dimmed">
            {report.note}
          </Text>
        )}

        <Group gap="sm">
          <Text size="xs" c="dimmed">
            {report.timing}
          </Text>
          <CheckedAgo readAt={run.dataUpdatedAt} />
          <Button
            size="xs"
            variant="default"
            leftSection={<IconRefresh size={14} />}
            loading={run.isFetching}
            onClick={onRefresh}
          >
            {copy.run.refresh}
          </Button>
        </Group>
      </Stack>
    </StatusAlert>
  );
}
