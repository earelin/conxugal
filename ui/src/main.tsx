// All Mantine packages except `@mantine/hooks` require their styles to be imported.
// @mantine/charts is imported by the metrics feature itself (features/administration/monitoring/metrics/index.ts),
// which is lazy-loaded, so its styles ship in that chunk instead of here.
import '@mantine/core/styles.css';

import { ColorSchemeScript, MantineProvider } from '@mantine/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router/dom';

import { router } from './app/router';
import { theme } from './app/theme';
import { queryClient } from './shared/lib/queryClient';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root container #root not found in index.html');
}

createRoot(container).render(
  <StrictMode>
    <ColorSchemeScript defaultColorScheme="auto" />
    <MantineProvider theme={theme} defaultColorScheme="auto">
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>
  </StrictMode>,
);
