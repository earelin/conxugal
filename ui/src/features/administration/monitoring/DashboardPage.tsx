import {
  ActionIcon,
  Alert,
  Badge,
  Card,
  Group,
  Loader,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import {
  IconAlertCircle,
  IconAlertTriangle,
  IconCircleCheck,
  IconLock,
  IconRefresh,
} from '@tabler/icons-react';
import { lazy, Suspense, type ReactNode } from 'react';
import { HttpError } from '../../../shared/lib/httpClient';
import { strings } from '../../../shared/lib/strings';
import { useSystemStatus, type SystemStatus } from './systemStatus';

// Code-split: @mantine/charts (and its recharts peer) are only needed once an
// ADMIN opens this page, not for every visitor of the app's eager entry chunk.
const MetricsPanel = lazy(() =>
  import('./metrics').then((module) => ({ default: module.MetricsPanel })),
);

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('gl-ES', { dateStyle: 'short', timeStyle: 'short' });
}

function formatUptime(uptimeMillis: number): string {
  const totalMinutes = Math.floor(uptimeMillis / 60_000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  return `${days} d ${String(hours).padStart(2, '0')} h ${String(minutes).padStart(2, '0')} m`;
}

function formatBytes(usedBytes: number, maxBytes: number): string {
  const toMb = (bytes: number) => Math.round(bytes / (1024 * 1024));
  return `${toMb(usedBytes)} / ${toMb(maxBytes)} MB`;
}

function DashboardError({ error }: { error: unknown }) {
  const message =
    error instanceof HttpError && error.status === 403
      ? strings.admin.dashboard.errorForbidden
      : strings.admin.dashboard.errorGeneric;

  return (
    <Alert
      color="red"
      icon={<IconAlertCircle size={18} />}
      title={strings.admin.dashboard.errorTitle}
    >
      {message}
    </Alert>
  );
}

function StatCard({ label, value, healthy }: { label: string; value: string; healthy: boolean }) {
  return (
    <Card component="section" aria-label={label} withBorder radius="md" padding="lg">
      <Text size="xs" fw={700} c="dimmed" tt="uppercase">
        {label}
      </Text>
      <Group justify="space-between" mt="xs">
        <Text size="xl" fw={600}>
          {value}
        </Text>
        <Badge color={healthy ? 'green' : 'red'} variant="light">
          {value}
        </Badge>
      </Group>
    </Card>
  );
}

function InfoField({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Stack gap={0}>
      <Text size="xs" c="dimmed" tt="uppercase">
        {label}
      </Text>
      <Text>{value}</Text>
    </Stack>
  );
}

function DashboardContent({
  status,
  isRefreshing,
  onRefresh,
}: {
  status: SystemStatus;
  isRefreshing: boolean;
  onRefresh: () => void;
}) {
  const isUp = status.status === 'UP';
  const serviceLabel = isUp
    ? strings.admin.dashboard.serviceUp
    : strings.admin.dashboard.serviceDegraded;
  const datastoreLabel = status.datastore.reachable
    ? strings.admin.dashboard.datastoreReachable
    : strings.admin.dashboard.datastoreUnreachable;

  return (
    <Stack gap="md">
      <Card withBorder radius="md" padding="lg">
        <Group justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            {isUp ? (
              <IconCircleCheck size={32} color="var(--mantine-color-green-6)" />
            ) : (
              <IconAlertTriangle size={32} color="var(--mantine-color-red-6)" />
            )}
            <Stack gap={0}>
              <Text fw={700} size="lg">
                {isUp
                  ? strings.admin.dashboard.statusUpTitle
                  : strings.admin.dashboard.statusDegradedTitle}
              </Text>
              <Text size="sm" c="dimmed">
                {isUp
                  ? strings.admin.dashboard.statusUpDescription
                  : strings.admin.dashboard.statusDegradedDescription}
              </Text>
            </Stack>
          </Group>
          <Group gap="xs" wrap="nowrap">
            <Text size="xs" c="dimmed">
              {strings.admin.dashboard.checkedAtLabel} {formatDateTime(status.checkedAt)}
            </Text>
            <ActionIcon
              variant="light"
              onClick={onRefresh}
              loading={isRefreshing}
              aria-label={strings.admin.dashboard.refresh}
            >
              <IconRefresh size={16} />
            </ActionIcon>
          </Group>
        </Group>
      </Card>

      <SimpleGrid cols={{ base: 1, sm: 2 }}>
        <StatCard
          label={strings.admin.dashboard.serviceLabel}
          value={serviceLabel}
          healthy={isUp}
        />
        <StatCard
          label={strings.admin.dashboard.datastoreLabel}
          value={datastoreLabel}
          healthy={status.datastore.reachable}
        />
      </SimpleGrid>

      <Card withBorder radius="md" padding="lg">
        <Text fw={700} mb="sm">
          {strings.admin.dashboard.systemInfoTitle}
        </Text>
        <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="lg">
          <InfoField
            label={strings.admin.dashboard.applicationVersionLabel}
            value={status.application.version}
          />
          <InfoField
            label={strings.admin.dashboard.environmentLabel}
            value={status.application.environment}
          />
          <InfoField
            label={strings.admin.dashboard.runtimeLabel}
            value={
              <>
                {status.runtime.javaVersion} ({status.runtime.javaVendor})
              </>
            }
          />
          <InfoField
            label={strings.admin.dashboard.uptimeLabel}
            value={formatUptime(status.runtime.uptimeMillis)}
          />
          <InfoField
            label={strings.admin.dashboard.memoryLabel}
            value={formatBytes(status.runtime.memoryUsedBytes, status.runtime.memoryMaxBytes)}
          />
          <InfoField
            label={strings.admin.dashboard.osLabel}
            value={
              <>
                {status.runtime.osName} ({status.runtime.osArch})
              </>
            }
          />
        </SimpleGrid>
        <Group gap="xs" mt="md">
          <IconLock size={14} color="var(--mantine-color-gray-6)" />
          <Text size="xs" c="dimmed">
            {strings.admin.dashboard.privacyNote}
          </Text>
        </Group>
      </Card>
    </Stack>
  );
}

export function DashboardPage() {
  const { data, isPending, isError, error, isFetching, refetch } = useSystemStatus();

  return (
    <Stack gap="md">
      <Stack gap={0}>
        <Title order={2}>{strings.admin.dashboard.title}</Title>
        <Text c="dimmed">{strings.admin.dashboard.subtitle}</Text>
      </Stack>

      {isPending && <Loader />}
      {isError && !data && <DashboardError error={error} />}
      {data && (
        <DashboardContent
          status={data}
          isRefreshing={isFetching}
          onRefresh={() => void refetch()}
        />
      )}
      <Suspense fallback={<Loader />}>
        <MetricsPanel />
      </Suspense>
    </Stack>
  );
}
