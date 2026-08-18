import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Testing Library's 1 s default budgets a wait against a state update. A test
// that mounts a route from `router.tsx` waits on a dynamic `import()` as well,
// and the admin section's chunk — @mantine/charts and its recharts peer — takes
// over a second to evaluate whenever the worker pool is busy, which is most of a
// full-suite run. That is test-infrastructure latency, not something the app
// promises, so the budget covers it rather than the suite failing by luck.
configure({ asyncUtilTimeout: 5_000 });

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

// jsdom lays nothing out and so implements no scrolling either. Mantine's
// Combobox scrolls its active option into view from a timer, which outlives the
// test that opened the dropdown: the TypeError surfaces as an uncaught
// exception attributed to whichever test happened to be running next.
Element.prototype.scrollIntoView = () => {};
