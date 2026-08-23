import eslintReact from '@eslint-react/eslint-plugin';
import js from '@eslint/js';
import vitest from '@vitest/eslint-plugin';
import { createTypeScriptImportResolver } from 'eslint-import-resolver-typescript';
import boundaries from 'eslint-plugin-boundaries';
import { importX } from 'eslint-plugin-import-x';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import noUnsanitized from 'eslint-plugin-no-unsanitized';
import perfectionist from 'eslint-plugin-perfectionist';
import playwright from 'eslint-plugin-playwright';
import prettier from 'eslint-plugin-prettier/recommended';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import youMightNotNeedAnEffect from 'eslint-plugin-react-you-might-not-need-an-effect';
import sonarjs from 'eslint-plugin-sonarjs';
import storybook from 'eslint-plugin-storybook';
import testingLibrary from 'eslint-plugin-testing-library';
import unusedImports from 'eslint-plugin-unused-imports';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// `*Harness.tsx` is test support rather than a suite: it holds the fixtures and
// the render helpers its siblings share. It lives beside them rather than in
// `src/test/` because that directory is the `app` element, and a shared-* test
// importing it would breach the dependency direction below.
const testFiles = ['**/*.test.{ts,tsx}', '**/*Harness.tsx', 'src/test/**/*.{ts,tsx}'];

const typescriptResolver = {
  alwaysTryTypes: true,
  project: [
    'tsconfig.app.json',
    'tsconfig.node.json',
    'tsconfig.acceptance.json',
    'tsconfig.storybook.json',
  ],
  noWarnOnMultipleProjects: true,
};

export default tseslint.config(
  // `storybook-static` is build output like `dist`, and it has to be named here
  // as well: ESLint reads no .gitignore, so without it a local `build-storybook`
  // leaves 8 MB of bundled JS for the type-aware rules to chew through.
  { ignores: ['dist', 'node_modules', 'coverage', 'storybook-static'] },
  js.configs.recommended,
  prettier,
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      tseslint.configs.recommendedTypeChecked,
      importX.flatConfigs.recommended,
      importX.flatConfigs.typescript,
      jsxA11y.flatConfigs.recommended,
      eslintReact.configs['recommended-typescript'],
      sonarjs.configs.recommended,
      noUnsanitized.configs.recommended,
    ],
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
      'unused-imports': unusedImports,
      boundaries,
      perfectionist,
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
        { pattern: '**/*Harness.tsx', category: 'test' },
      ],
      // acceptance/ drives the built app over HTTP and imports nothing from src, so it
      // sits outside the module graph these rules describe. .storybook/ is the other
      // way round — it is a second composition root, so like main.tsx it reaches the
      // theme and the query client, and the layers below have no business seeing it.
      'boundaries/ignore': [
        'src/vite-env.d.ts',
        'src/App.test.tsx',
        'vite.config.ts',
        'playwright.config.ts',
        'acceptance/**',
        '.storybook/**',
      ],
      // `import/resolver` is eslint-plugin-boundaries' resolver (it reads the
      // eslint-plugin-import key); `import-x/resolver-next` is import-x's own.
      // Both must stay — they are read by different plugins.
      'import/resolver': { typescript: typescriptResolver },
      'import-x/resolver-next': [createTypeScriptImportResolver(typescriptResolver)],
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      // eslint-plugin-react-hooks stays the authority on hook usage; these two
      // eslint-react rules are the same checks and would double-report.
      '@eslint-react/rules-of-hooks': 'off',
      '@eslint-react/exhaustive-deps': 'off',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // nock's documented API is `import nock from 'nock'; nock.cleanAll()`, but it
      // re-exports every method as a named export too, so the rule fires on correct code.
      'import-x/no-named-as-default-member': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      'unused-imports/no-unused-imports': 'error',
      'unused-imports/no-unused-vars': [
        'error',
        {
          vars: 'all',
          varsIgnorePattern: '^_',
          args: 'after-used',
          argsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
      // Only the import/export half of perfectionist is on. Its sorters for
      // objects, JSX props, interfaces, modules and classes reorder code whose
      // existing order carries meaning (prop grouping, field lifecycle, related
      // keys kept adjacent), so they trade readability for alphabetisation.
      //
      // The groups below keep a source's type and value imports together rather
      // than hoisting every `import type` into a block of its own, and stop at
      // "third-party vs. local" — which layer a local path belongs to is
      // already enforced by boundaries, so splitting it finer here adds noise.
      'perfectionist/sort-imports': [
        'error',
        {
          type: 'natural',
          groups: [
            'side-effect-style',
            ['builtin', 'external'],
            ['internal', 'parent', 'sibling', 'index'],
          ],
          newlinesBetween: 1,
        },
      ],
      'perfectionist/sort-named-imports': ['error', { type: 'natural' }],
      'perfectionist/sort-exports': ['error', { type: 'natural' }],
      'perfectionist/sort-named-exports': ['error', { type: 'natural' }],
      // A React component returning either an element or its `ReactNode`
      // fallback prop is idiomatic, not an inconsistent return type.
      'sonarjs/function-return-type': 'off',
      // Would wrap every component's props type in `Readonly<>`. React never
      // mutates props, and TypeScript would not catch it if it did — the
      // annotation buys no defect class this module can actually hit.
      'sonarjs/prefer-read-only-props': 'off',
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
  { ...youMightNotNeedAnEffect.configs.recommended, files: ['**/*.{ts,tsx}'] },
  {
    files: testFiles,
    extends: [vitest.configs.recommended, testingLibrary.configs['flat/react']],
    rules: {
      // Both fire on the same pattern: asserting a class or attribute that has
      // no accessible handle to query by — Mantine's responsive
      // `visible-from-sm`/`hidden-from-sm` classes, and the `aria-hidden`
      // + `inert` wrapper around a decorative sparkline. An element hidden
      // from the accessibility tree is by definition unreachable through
      // Testing Library's queries, so the container is the only way in.
      'testing-library/no-container': 'off',
      'testing-library/no-node-access': 'off',
      // The four below are production-code rules that only misfire on test
      // idioms: a stubbed API response carrying a fake password, exact
      // assertions on values a float represents exactly (0.35, 0.05), the
      // explicit `undefined` argument that is the point of the case under
      // test, and a mutable static registry a fake reassigns to reset itself.
      'sonarjs/no-floating-point-equality': 'off',
      'sonarjs/no-hardcoded-passwords': 'off',
      'sonarjs/no-undefined-argument': 'off',
      'sonarjs/public-static-readonly': 'off',
    },
  },
  {
    files: ['src/test/setup.ts'],
    rules: {
      // Vitest globals are off, so Testing Library never sees an `afterEach` to
      // hook its automatic cleanup onto — registering it by hand is required.
      'testing-library/no-manual-cleanup': 'off',
    },
  },
  {
    files: ['acceptance/**/*.{ts,tsx}'],
    extends: [playwright.configs['flat/recommended']],
  },
  // Carries its own `files` globs (`**/*.stories.*` plus `.storybook/main.*`),
  // so it is spread rather than scoped here.
  ...storybook.configs['flat/recommended'],
  {
    files: ['**/*.stories.{ts,tsx}'],
    rules: {
      // A CSF file is a default-exported meta object beside named story
      // objects, which is the exact shape this rule exists to reject. Nothing
      // in Storybook is hot-reloaded by React Refresh's component boundary.
      'react-refresh/only-export-components': 'off',
    },
  },
  {
    files: [
      'vite.config.ts',
      'playwright.config.ts',
      'acceptance/**/*.ts',
      '.storybook/**/*.{ts,tsx}',
    ],
    languageOptions: {
      globals: globals.node,
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
