import type { StorybookConfig } from '@storybook/react-vite';

// `typescript.reactDocgen` is left at Storybook's default, the JS-based
// `react-docgen`, which reads this module's `interface XxxProps` declarations
// without ever resolving the `typescript` package. `react-docgen-typescript`
// would resolve it — and here that name is the `@typescript/typescript6`
// compatibility shim rather than the TypeScript 7 compiler the build uses.
const config: StorybookConfig = {
  stories: ['../src/**/*.stories.@(ts|tsx)'],
  addons: ['@storybook/addon-docs', '@storybook/addon-a11y'],
  // `strictMode` matches `src/main.tsx`, which renders inside `StrictMode`.
  // Without it the workshop hides exactly the double-invocation and cleanup
  // faults the app would surface — the wrong way round for a place components
  // are built before they reach a route.
  framework: { name: '@storybook/react-vite', options: { strictMode: true } },
  // Nothing about this workshop is worth phoning home for, least of all from CI.
  core: { disableTelemetry: true },
};

export default config;
