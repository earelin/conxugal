import { MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import nock from 'nock';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { routes } from '../app/router';
import { theme } from '../app/theme';
import type { Role } from '../shared/entities/currentUser';
import { createQueryClient } from '../shared/lib/queryClient';

export const BASE_URL = 'http://localhost:3000';

export function mockCurrentUser(role: Role) {
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

export function renderApp(initialPath = '/') {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  return render(
    <MantineProvider theme={theme}>
      <QueryClientProvider client={createQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>,
  );
}
