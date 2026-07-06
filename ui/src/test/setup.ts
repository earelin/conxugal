import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Vitest globals are disabled, so register Testing Library's DOM cleanup
// explicitly to unmount between tests.
afterEach(() => {
  cleanup();
});

// jsdom does not implement these browser APIs that Mantine relies on; provide
// minimal mocks so components render in tests.
const matchMediaMock = (query: string): MediaQueryList => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
});

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

vi.stubGlobal('matchMedia', matchMediaMock);
vi.stubGlobal('ResizeObserver', ResizeObserverMock);
