import '@mantine/core/styles.css';
// Not the divergence from `src/main.tsx` it looks like: the app defers this
// stylesheet into the lazy admin chunk (`features/administration/monitoring/
// metrics/index.ts`) because only that chunk draws a chart. Storybook has no
// such chunk — a story renders its component and nothing else — so the metric
// tiles and the sparkline would come up unstyled without it here.
import '@mantine/charts/styles.css';

import { MantineProvider } from '@mantine/core';
import type { Preview } from '@storybook/react-vite';
import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { createMemoryRouter, type RouteObject } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { theme } from '../src/app/theme';
import { createQueryClient } from '../src/shared/lib/queryClient';

const preview: Preview = {
  parameters: {
    controls: { matchers: { color: /(background|color)$/i, date: /Date$/i } },
  },

  // The app resolves its scheme from the system (`defaultColorScheme="auto"`),
  // so dark is a state readers actually meet. Storybook's own chrome follows
  // the browser, and this names the scheme the component renders under.
  globalTypes: {
    colorScheme: {
      description: 'Esquema de cor',
      toolbar: {
        title: 'Cor',
        icon: 'circlehollow',
        items: [
          { value: 'light', title: 'Claro' },
          { value: 'dark', title: 'Escuro' },
        ],
        dynamicTitle: true,
      },
    },
  },
  initialGlobals: { colorScheme: 'light' },

  decorators: [
    (Story, { globals, parameters }) => {
      // Built once per mount. Storybook keys the story tree on the story id, so
      // switching stories remounts this and no client is shared between two of
      // them; rebuilding it on every render would instead throw away a mutation
      // left pending whenever an arg or the colour-scheme toolbar changed.
      const [queryClient] = useState(createQueryClient);

      // A router, because two of the components stored here read one:
      // `OrganoPicker` resolves the open Órgano from the location and
      // `FamilyTabs` renders router links. A splat route matches whatever path
      // a story asks for — the shape the picker's test harness uses.
      //
      // Deliberately NOT memoised, unlike the client above. The route holds a
      // `<Story />` element, and `Story` carries the args of the render that
      // made it: hold the router across renders and every control in the panel
      // stops working, because the frozen element keeps rendering the first
      // args forever. Rebuilding remounts the story on an args change, which is
      // the price of the controls doing anything at all.
      const routes: RouteObject[] = [{ path: '*', element: <Story /> }];
      // `parameters.initialPath` lets a story mount at a route its component
      // reads — the picker draws a different trigger on `/organo/:id`.
      const initialPath = typeof parameters.initialPath === 'string' ? parameters.initialPath : '/';
      const router = createMemoryRouter(routes, { initialEntries: [initialPath] });

      return (
        <MantineProvider theme={theme} forceColorScheme={globals.colorScheme as 'light' | 'dark'}>
          <QueryClientProvider client={queryClient}>
            <RouterProvider router={router} />
          </QueryClientProvider>
        </MantineProvider>
      );
    },
  ],
};

export default preview;
