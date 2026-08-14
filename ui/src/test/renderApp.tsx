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

export function currentUserBody(role: Role) {
  return {
    id: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
    email: `${role.toLowerCase()}@conxugal.gal`,
    role,
    createdAt: '2026-01-15T09:30:00Z',
    lastLoginAt: '2026-07-10T08:12:00Z',
  };
}

export function mockCurrentUser(role: Role) {
  return nock(BASE_URL).get('/api/me').reply(200, currentUserBody(role));
}

export const PICKER_ORGANOS = [
  { id: 'o-1', name: 'Servizo Galego de Saúde', active: true, termoId: 't-1' },
  { id: 'o-2', name: 'Instituto Galego da Vivenda e Solo', active: true, termoId: null },
];

export const PICKER_TERMOS = [{ id: 't-1', name: 'Consellería de Sanidade', parentId: null }];

/**
 * The two reads the navbar picker makes as soon as a session resolves. Any test
 * that mounts the shell with a signed-in user needs them: `nock` fails an
 * unmatched request, and those failures land after the test has torn down.
 */
export function mockOrganosPicker(organos = PICKER_ORGANOS, termos = PICKER_TERMOS) {
  return nock(BASE_URL)
    .get('/api/organos')
    .reply(200, organos)
    .get('/api/organos/taxonomia')
    .reply(200, termos);
}

export function renderApp(initialPath = '/') {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  const queryClient = createQueryClient();
  const utils = render(
    // `env="test"` turns off Mantine's transitions: an overlay spends its first
    // frames `display: none`, which hides its contents from every accessible
    // query for as long as the transition runs.
    <MantineProvider theme={theme} env="test">
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>,
  );
  // Handed back so a test can ask what the shell has actually asked the server
  // for, which is not something the rendered output can answer.
  return { ...utils, queryClient };
}
