import { screen, waitFor } from '@testing-library/react';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { strings } from '../shared/lib/strings';
import { BASE_URL, mockCurrentUser, renderApp } from '../test/renderApp';

function mockSystemStatus() {
  return nock(BASE_URL)
    .get('/api/admin/system-status')
    .reply(200, {
      status: 'UP',
      datastore: { reachable: true },
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
    });
}

describe('AdminRoute', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('renders the dashboard for an ADMIN session', async () => {
    mockCurrentUser('ADMIN');
    mockSystemStatus();
    renderApp('/administracion');

    expect(await screen.findByText(strings.admin.dashboard.title)).toBeInTheDocument();
  });

  it('does not render the dashboard for a USER session, showing the not-found page instead', async () => {
    const scope = mockCurrentUser('USER');
    renderApp('/administracion');

    await waitFor(() => expect(scope.isDone()).toBe(true));

    expect(await screen.findByText(strings.notFound.title)).toBeInTheDocument();
    // The section is code-split, so without waiting for its module the negative
    // below would pass simply by outrunning the import — proving nothing about
    // the guard. The guard itself is what starts that fetch, role or no role.
    await import('../features/administration');
    expect(screen.queryByText(strings.admin.dashboard.title)).not.toBeInTheDocument();
  });

  it('does not render the Usuarios placeholder for a USER session', async () => {
    const scope = mockCurrentUser('USER');
    renderApp('/administracion/usuarios');

    await waitFor(() => expect(scope.isDone()).toBe(true));

    expect(await screen.findByText(strings.notFound.title)).toBeInTheDocument();
    await import('../features/administration');
    expect(screen.queryByText(strings.admin.users.title)).not.toBeInTheDocument();
  });
});
