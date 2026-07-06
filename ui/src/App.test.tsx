import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { describe, expect, it } from 'vitest';
import { routes } from './router';
import { strings } from './strings';
import { theme } from './theme';

function renderAt(initialPath: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  return render(
    <MantineProvider theme={theme}>
      <RouterProvider router={router} />
    </MantineProvider>,
  );
}

describe('application shell', () => {
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
