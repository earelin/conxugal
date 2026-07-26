import { MantineProvider } from '@mantine/core';
import { type QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { theme } from '../../../app/theme';
import { createQueryClient } from '../../../shared/lib/queryClient';
import { strings } from '../../../shared/lib/strings';
import { DashboardPage } from './DashboardPage';

const BASE_URL = 'http://localhost:3000';

const baseStatus = {
  checkedAt: '2026-07-18T09:30:00Z',
  application: { version: '0.1.0-SNAPSHOT', environment: 'production' },
  runtime: {
    javaVersion: '21.0.3',
    javaVendor: 'Eclipse Adoptium',
    uptimeMillis: 266_320_000,
    memoryUsedBytes: 536_870_912,
    memoryMaxBytes: 1_073_741_824,
    osName: 'Linux',
    osArch: 'amd64',
  },
};

function mockSystemStatus(overrides: { status: 'UP' | 'DEGRADED'; reachable: boolean }) {
  return nock(BASE_URL)
    .get('/api/admin/system-status')
    .reply(200, {
      ...baseStatus,
      status: overrides.status,
      datastore: { reachable: overrides.reachable },
    });
}

function renderDashboard(queryClient: QueryClient = createQueryClient()) {
  return render(
    <MantineProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <DashboardPage />
      </QueryClientProvider>
    </MantineProvider>,
  );
}

describe('DashboardPage', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('renders the overall service state and datastore reachability when up', async () => {
    mockSystemStatus({ status: 'UP', reachable: true });
    renderDashboard();

    expect(await screen.findByText(strings.admin.dashboard.statusUpTitle)).toBeInTheDocument();
    expect(screen.getAllByText(strings.admin.dashboard.serviceUp).length).toBeGreaterThan(0);
    expect(screen.getAllByText(strings.admin.dashboard.datastoreReachable).length).toBeGreaterThan(
      0,
    );
  });

  it('renders the degraded state and unreachable datastore', async () => {
    mockSystemStatus({ status: 'DEGRADED', reachable: false });
    renderDashboard();

    expect(
      await screen.findByText(strings.admin.dashboard.statusDegradedTitle),
    ).toBeInTheDocument();
    expect(screen.getAllByText(strings.admin.dashboard.serviceDegraded).length).toBeGreaterThan(0);
    expect(
      screen.getAllByText(strings.admin.dashboard.datastoreUnreachable).length,
    ).toBeGreaterThan(0);
  });

  it('shows a Galician forbidden message when the server denies the request', async () => {
    nock(BASE_URL).get('/api/admin/system-status').reply(403);
    renderDashboard();

    expect(await screen.findByText(strings.admin.dashboard.errorForbidden)).toBeInTheDocument();
    expect(screen.queryByText(strings.admin.dashboard.serviceLabel)).not.toBeInTheDocument();
  });

  it('shows a generic Galician error message for a non-403 failure', async () => {
    nock(BASE_URL).get('/api/admin/system-status').reply(500);
    renderDashboard();

    expect(await screen.findByText(strings.admin.dashboard.errorGeneric)).toBeInTheDocument();
    expect(screen.queryByText(strings.admin.dashboard.errorForbidden)).not.toBeInTheDocument();
  });

  it('keeps showing the last-good status instead of an error banner when a refresh fails', async () => {
    mockSystemStatus({ status: 'UP', reachable: true });
    renderDashboard();
    await screen.findByText(strings.admin.dashboard.statusUpTitle);

    const scope = nock(BASE_URL).get('/api/admin/system-status').reply(500);
    screen.getByRole('button', { name: strings.admin.dashboard.refresh }).click();

    await waitFor(() => expect(scope.isDone()).toBe(true));
    await waitFor(() => {
      expect(screen.getByText(strings.admin.dashboard.statusUpTitle)).toBeInTheDocument();
      expect(screen.queryByText(strings.admin.dashboard.errorTitle)).not.toBeInTheDocument();
    });
  });

  it('renders only endpoint-returned fields, never fabricated stats', async () => {
    mockSystemStatus({ status: 'UP', reachable: true });
    renderDashboard();

    await screen.findByText(strings.admin.dashboard.statusUpTitle);
    expect(screen.getByText('0.1.0-SNAPSHOT')).toBeInTheDocument();
    expect(screen.queryByText(/incidencias/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/sonda/i)).not.toBeInTheDocument();
  });

  it('reflects a changed dependency state on the next dashboard view instead of a cached one', async () => {
    const queryClient = createQueryClient();

    mockSystemStatus({ status: 'UP', reachable: true });
    const { unmount } = renderDashboard(queryClient);
    await screen.findByText(strings.admin.dashboard.statusUpTitle);
    unmount();

    mockSystemStatus({ status: 'DEGRADED', reachable: false });
    renderDashboard(queryClient);

    expect(
      await screen.findByText(strings.admin.dashboard.statusDegradedTitle),
    ).toBeInTheDocument();
    expect(screen.queryByText(strings.admin.dashboard.statusUpTitle)).not.toBeInTheDocument();
  });
});
