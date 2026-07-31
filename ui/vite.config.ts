/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// The SPA calls same-origin relative paths (`/api/...`, `/logout`) because in
// production one server serves both the assets and the API. Locally that origin
// is the WireMock stub from docker-compose.yml; point it at a real backend with
// UI_API_TARGET=http://localhost:8080.
const apiTarget = process.env.UI_API_TARGET ?? 'http://localhost:8081';

const proxy = Object.fromEntries(
  ['/api', '/login', '/logout'].map((path) => [path, { target: apiTarget, changeOrigin: true }]),
);

export default defineConfig({
  plugins: [react()],
  base: '/',
  server: {
    port: 5173,
    proxy,
  },
  preview: {
    port: 4173,
    proxy,
  },
  test: {
    // Scoped to src/ so Vitest doesn't collect the Playwright specs in e2e/,
    // which its default `**/*.spec.ts` glob would otherwise match.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
    env: {
      // Small enough that tests filling/evicting the metrics history buffer
      // stay fast, without needing a raised per-test timeout.
      VITE_METRICS_HISTORY_LIMIT: '10',
    },
  },
});
