import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import nock from 'nock';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createQueryClient } from '../api/queryClient';
import { routes } from '../router';
import { strings } from '../strings';
import { theme } from '../theme';

const BASE_URL = 'http://localhost:3000';

function mockCurrentUser(role: 'ADMIN' | 'USER') {
  return nock(BASE_URL)
    .get('/api/me')
    .reply(200, {
      id: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
      email: `${role.toLowerCase()}@conxugal.gal`,
      role,
      createdAt: '2026-01-15T09:30:00Z',
      lastLoginAt: '2026-07-10T08:12:00Z',
    });
}

function renderShell() {
  const router = createMemoryRouter(routes, { initialEntries: ['/'] });
  return render(
    <MantineProvider theme={theme}>
      <QueryClientProvider client={createQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>,
  );
}

describe('AppLayout admin navigation', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('shows the administration section for an ADMIN session', async () => {
    mockCurrentUser('ADMIN');
    renderShell();

    expect(await screen.findByText(strings.nav.adminSection)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.panel })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.users })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.home })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.about })).toBeInTheDocument();
  });

  it('hides the administration section for a USER session', async () => {
    const scope = mockCurrentUser('USER');
    renderShell();

    await waitFor(() => expect(scope.isDone()).toBe(true));

    expect(screen.queryByText(strings.nav.adminSection)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: strings.nav.panel })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: strings.nav.users })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.home })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: strings.nav.about })).toBeInTheDocument();
  });
});
