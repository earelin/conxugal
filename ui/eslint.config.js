import js from '@eslint/js';
import boundaries from 'eslint-plugin-boundaries';
import prettier from 'eslint-plugin-prettier/recommended';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'coverage'] },
  js.configs.recommended,
  prettier,
  {
    files: ['**/*.{ts,tsx}'],
    extends: [tseslint.configs.recommendedTypeChecked],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      boundaries,
    },
    settings: {
      'boundaries/elements': [
        { type: 'app', pattern: 'src/app' },
        { type: 'app', pattern: 'src/test' },
        { type: 'features', pattern: 'src/features/*', capture: ['feature'] },
        { type: 'shared-entities', pattern: 'src/shared/entities' },
        { type: 'shared-ui', pattern: 'src/shared/ui' },
        { type: 'shared-lib', pattern: 'src/shared/lib' },
      ],
      'boundaries/files': [
        { pattern: 'src/main.tsx', category: 'app-entry' },
        { pattern: '**/*.test.{ts,tsx}', category: 'test' },
      ],
      'boundaries/ignore': ['src/vite-env.d.ts', 'src/App.test.tsx', 'vite.config.ts'],
      'import/resolver': {
        typescript: {
          alwaysTryTypes: true,
          project: ['tsconfig.app.json', 'tsconfig.node.json'],
          noWarnOnMultipleProjects: true,
        },
      },
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      'boundaries/no-unknown-files': 'error',
      'boundaries/no-unknown-dependencies': 'error',
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            {
              from: { file: { categories: 'app-entry' } },
              allow: {
                to: [
                  { element: { type: 'app' } },
                  { element: { type: 'features', fileInternalPath: 'index.{ts,tsx}' } },
                  { element: { type: ['shared-entities', 'shared-ui', 'shared-lib'] } },
                ],
              },
            },
            {
              from: { element: { type: 'app' } },
              allow: {
                to: [
                  { element: { type: 'app' } },
                  { element: { type: 'features', fileInternalPath: 'index.{ts,tsx}' } },
                  { element: { type: ['shared-entities', 'shared-ui', 'shared-lib'] } },
                ],
              },
            },
            {
              from: { element: { type: 'features' } },
              allow: {
                to: [
                  {
                    element: {
                      type: 'features',
                      captured: { feature: '{{ from.element.captured.feature }}' },
                    },
                  },
                  { element: { type: ['shared-entities', 'shared-ui', 'shared-lib'] } },
                ],
              },
            },
            {
              from: { element: { type: 'shared-entities' } },
              allow: { to: { element: { type: ['shared-entities', 'shared-ui', 'shared-lib'] } } },
            },
            {
              from: { element: { type: 'shared-ui' } },
              allow: { to: { element: { type: ['shared-ui', 'shared-lib'] } } },
            },
            {
              from: { element: { type: 'shared-lib' } },
              allow: { to: { element: { type: 'shared-lib' } } },
            },
            {
              // Feature tests may additionally reach into the app layer (e.g. the
              // shared test harness in src/test/, or app/theme.ts for a
              // MantineProvider wrapper) without loosening what production
              // code — or a test importing another feature's internals — may do.
              // Scoped to `features` only: `app` can already reach `app`, and
              // `shared-*` tests have no legitimate reason to depend upward on `app`.
              from: { element: { type: 'features' }, file: { categories: 'test' } },
              allow: { to: { element: { type: 'app' } } },
            },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.cjs'],
    languageOptions: {
      sourceType: 'commonjs',
      globals: globals.node,
    },
  },
);
