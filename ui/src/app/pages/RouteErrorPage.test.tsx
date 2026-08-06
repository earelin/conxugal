import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, type RouteObject } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { strings } from '../../shared/lib/strings';
import { theme } from '../theme';
import { RouteErrorPage } from './RouteErrorPage';

/**
 * Rendered through a router that throws, because the component reads the error
 * off the route context — handing it a prop would test a different component.
 */
function renderAfterThrowing(error: unknown) {
  function Boom(): never {
    throw error;
  }

  const routes: RouteObject[] = [{ path: '/', Component: Boom, errorElement: <RouteErrorPage /> }];

  return render(
    <MantineProvider theme={theme}>
      <RouterProvider router={createMemoryRouter(routes)} />
    </MantineProvider>,
  );
}

describe('RouteErrorPage', () => {
  // Restores this one global rather than calling `vi.unstubAllGlobals()`, which
  // would also drop the matchMedia/ResizeObserver/EventSource stubs that
  // `src/test/setup.ts` installs once for the whole suite.
  const realLocation = globalThis.location;
  afterEach(() => {
    vi.stubGlobal('location', realLocation);
  });

  it('blames a redeployment when a code-split section failed to download', () => {
    renderAfterThrowing(
      new TypeError('Failed to fetch dynamically imported module: /assets/organos-abc123.js'),
    );

    expect(screen.getByText(strings.routeError.staleDeployment)).toBeInTheDocument();
    expect(screen.queryByText(strings.routeError.unexpected)).not.toBeInTheDocument();
  });

  it('does not blame a redeployment for a section that crashed while rendering', () => {
    renderAfterThrowing(new TypeError('Cannot read properties of undefined (reading "nome")'));

    expect(screen.getByText(strings.routeError.unexpected)).toBeInTheDocument();
    expect(screen.queryByText(strings.routeError.staleDeployment)).not.toBeInTheDocument();
  });

  it('reloads the document on retry, the only thing that clears a cached failed import', async () => {
    // jsdom's own `location.reload` cannot be redefined, so the whole global is
    // swapped rather than spied on.
    const reload = vi.fn();
    vi.stubGlobal('location', { ...globalThis.location, reload });

    renderAfterThrowing(new Error('Failed to fetch dynamically imported module: /assets/x.js'));
    await userEvent.click(screen.getByRole('button', { name: strings.retry }));

    expect(reload).toHaveBeenCalledOnce();
  });
});
