import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import nock from 'nock';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createQueryClient } from './api/queryClient';
import { routes } from './router';
import { strings } from './strings';
import { theme } from './theme';

const BASE_URL = 'http://localhost:3000';

function renderAt(initialPath: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  return render(
    <MantineProvider theme={theme}>
      <QueryClientProvider client={createQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>,
  );
}

describe('application shell', () => {
  beforeEach(() => {
    nock.disableNetConnect();
    nock(BASE_URL).get('/api/me').reply(200, {
      id: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
      email: 'user@conxugal.gal',
      role: 'USER',
      createdAt: '2026-01-15T09:30:00Z',
      lastLoginAt: '2026-07-10T08:12:00Z',
    });
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('renders the shell with the product name and home content on /', () => {
    renderAt('/');

    expect(screen.getByRole('heading', { level: 1, name: strings.appName })).toBeInTheDocument();
    expect(screen.getByText(strings.home.title)).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toBeInTheDocument();
  });

  it('shows the Galician not-found message inside the shell for unknown routes', () => {
    renderAt('/rota-que-non-existe');

    expect(screen.getByRole('heading', { level: 1, name: strings.appName })).toBeInTheDocument();
    expect(screen.getByText(strings.notFound.title)).toBeInTheDocument();
    expect(screen.getByText(strings.notFound.description)).toBeInTheDocument();
  });
});
