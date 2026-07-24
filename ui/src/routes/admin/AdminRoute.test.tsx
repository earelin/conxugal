import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { describe, expect, it, vi } from 'vitest';
import { useCurrentUser } from '../../api/currentUser';
import { strings } from '../../strings';
import { theme } from '../../theme';
import { AdminRoute } from './AdminRoute';

vi.mock('../../api/currentUser', () => ({ useCurrentUser: vi.fn() }));

const mockedUseCurrentUser = vi.mocked(useCurrentUser);

function mockCurrentUserResult(result: Partial<ReturnType<typeof useCurrentUser>>) {
  mockedUseCurrentUser.mockReturnValue(result as ReturnType<typeof useCurrentUser>);
}

function renderAdminRoute() {
  const router = createMemoryRouter(
    [
      {
        path: '/',
        children: [
          {
            path: 'administracion',
            Component: AdminRoute,
            children: [{ index: true, Component: () => <div>Admin content</div> }],
          },
        ],
      },
    ],
    { initialEntries: ['/administracion'] },
  );
  return render(
    <MantineProvider theme={theme}>
      <RouterProvider router={router} />
    </MantineProvider>,
  );
}

describe('AdminRoute', () => {
  it('shows a loader while the session is resolving', () => {
    mockCurrentUserResult({ data: undefined, isPending: true });
    const { container } = renderAdminRoute();

    expect(container.querySelector('.mantine-Loader-root')).toBeInTheDocument();
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument();
  });

  it('renders the nested route for an ADMIN session', () => {
    mockCurrentUserResult({
      isPending: false,
      data: {
        id: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        email: 'admin@conxugal.gal',
        role: 'ADMIN',
        createdAt: '2026-01-15T09:30:00Z',
        lastLoginAt: null,
      },
    });
    renderAdminRoute();

    expect(screen.getByText('Admin content')).toBeInTheDocument();
  });

  it('renders the not-found page instead of the nested route for a USER session', () => {
    mockCurrentUserResult({
      isPending: false,
      data: {
        id: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        email: 'user@conxugal.gal',
        role: 'USER',
        createdAt: '2026-01-15T09:30:00Z',
        lastLoginAt: null,
      },
    });
    renderAdminRoute();

    expect(screen.getByText(strings.notFound.title)).toBeInTheDocument();
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument();
  });

  it('renders the not-found page when the session failed to resolve', () => {
    mockCurrentUserResult({ isPending: false, data: undefined });
    renderAdminRoute();

    expect(screen.getByText(strings.notFound.title)).toBeInTheDocument();
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument();
  });
});
