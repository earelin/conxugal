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
  constructor(private callback: ResizeObserverCallback) {}

  observe(target: Element) {
    // Chart libraries (recharts' ResponsiveContainer, used by
    // @mantine/charts' Sparkline) measure their container via
    // ResizeObserver. jsdom never lays anything out, so without a
    // synthetic non-zero size every sparkline warns "width(0) and
    // height(0) of chart should be greater than 0" on every render.
    this.callback(
      [{ target, contentRect: { width: 300, height: 40 } } as ResizeObserverEntry],
      this,
    );
  }

  unobserve() {}
  disconnect() {}
}

// jsdom has no EventSource either. This inert stub never fires a callback, so
// a component that opens one (e.g. the admin metrics panel) just sits in its
// permanent "connecting" state during tests that don't stub EventSource
// themselves with a more capable double.
class NoopEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;

  readyState = NoopEventSource.CONNECTING;
  onopen: (() => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(public url: string) {}

  close() {
    this.readyState = NoopEventSource.CLOSED;
  }

  addEventListener() {}
  removeEventListener() {}
  dispatchEvent(): boolean {
    return false;
  }
}

vi.stubGlobal('matchMedia', matchMediaMock);
vi.stubGlobal('ResizeObserver', ResizeObserverMock);
vi.stubGlobal('EventSource', NoopEventSource);
