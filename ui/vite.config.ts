/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  base: '/',
  server: {
    port: 5173,
  },
  test: {
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
