import { MantineProvider } from '@mantine/core';
import { act, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { theme } from '../../../app/theme';
import { strings } from '../../../shared/lib/strings';
import { MetricsPanel } from './MetricsPanel';
import type { RuntimeMetrics } from './metricsStream';

const t = strings.admin.dashboard.metrics;

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  readyState = 0;
  onopen: (() => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: (() => void) | null = null;
  close = vi.fn(() => {
    this.readyState = 2;
  });

  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }

  emitMessage(sample: RuntimeMetrics) {
    this.readyState = 1;
    this.onmessage?.({ data: JSON.stringify(sample) } as MessageEvent);
  }

  emitError() {
    this.onerror?.();
  }
}

function currentSource(): FakeEventSource {
  const source = FakeEventSource.instances.at(-1);
  if (!source) {
    throw new Error('No EventSource instance has been constructed yet');
  }
  return source;
}

function renderMetricsPanel() {
  return render(
    <MantineProvider theme={theme}>
      <MetricsPanel />
    </MantineProvider>,
  );
}

const baseSample: RuntimeMetrics = {
  timestamp: '2026-07-18T09:30:00Z',
  jvm: {
    heapUsedBytes: 536_870_912,
    heapMaxBytes: 1_073_741_824,
    nonHeapUsedBytes: 100_663_296,
    threadCount: 42,
    uptimeMillis: 3_600_000,
    gcCollectionCount: 17,
    gcCollectionTimeMillis: 250,
  },
  system: { cpuLoad: 0.35 },
  http: { requestCount: 15_230, errorCount: 12 },
  datastorePool: { active: 3, idle: 7, max: 10 },
};

describe('MetricsPanel', () => {
  beforeEach(() => {
    vi.stubGlobal('EventSource', FakeEventSource);
    FakeEventSource.instances = [];
  });

  it('starts connecting and moves to live on the first sample', () => {
    renderMetricsPanel();

    expect(screen.getByText(t.stateConnecting)).toBeInTheDocument();
    expect(screen.getByText(t.awaitingFirstSample)).toBeInTheDocument();

    act(() => currentSource().emitMessage(baseSample));

    expect(screen.getByText(t.stateLive)).toBeInTheDocument();
    expect(screen.queryByText(t.awaitingFirstSample)).not.toBeInTheDocument();
    expect(screen.getByText('50 %')).toBeInTheDocument();
    expect(screen.getAllByText('15 230').length).toBeGreaterThan(0);
  });

  it('updates values and grows the bounded history as more samples arrive', () => {
    renderMetricsPanel();

    act(() => currentSource().emitMessage(baseSample));
    expect(screen.getAllByText(/1\/30/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/2\/30/)).not.toBeInTheDocument();

    act(() =>
      currentSource().emitMessage({
        ...baseSample,
        timestamp: '2026-07-18T09:30:05Z',
        jvm: { ...baseSample.jvm, heapUsedBytes: 750_000_000 },
        http: { requestCount: 15_268, errorCount: 12 },
      }),
    );

    expect(screen.getAllByText(/2\/30/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/1\/30/)).not.toBeInTheDocument();
    expect(screen.getByText(`+38 ${t.sinceLastSamplePrefix}`)).toBeInTheDocument();
  });

  it('stays connecting when the stream errors before any sample has arrived', () => {
    renderMetricsPanel();

    act(() => currentSource().emitError());

    expect(screen.getByText(t.stateConnecting)).toBeInTheDocument();
    expect(screen.getByText(t.awaitingFirstSample)).toBeInTheDocument();
  });

  it('keeps the last known values, dimmed, when the stream drops after a sample', () => {
    renderMetricsPanel();

    act(() => currentSource().emitMessage(baseSample));
    act(() => currentSource().emitError());

    expect(screen.getByText(t.stateReconnecting)).toBeInTheDocument();
    expect(screen.getByText('50 %')).toBeInTheDocument();
    expect(screen.getByText(new RegExp(t.notUpdating))).toBeInTheDocument();
    expect(screen.queryByText(/1\/30/)).not.toBeInTheDocument();
  });

  it('closes the EventSource on unmount', () => {
    const { unmount } = renderMetricsPanel();
    const source = currentSource();

    unmount();

    expect(source.close).toHaveBeenCalledTimes(1);
  });

  it('starts empty again on a fresh mount', () => {
    const { unmount } = renderMetricsPanel();
    act(() => currentSource().emitMessage(baseSample));
    expect(screen.getByText('50 %')).toBeInTheDocument();
    unmount();

    renderMetricsPanel();

    expect(screen.getByText(t.stateConnecting)).toBeInTheDocument();
    expect(screen.queryByText('50 %')).not.toBeInTheDocument();
  });

  it('renders only fields present in the sample, never a fabricated value', () => {
    renderMetricsPanel();

    act(() => currentSource().emitMessage({ timestamp: '2026-07-18T09:30:00Z' }));

    expect(screen.getAllByText(t.noValue).length).toBeGreaterThan(0);
    expect(screen.queryByText('0 %')).not.toBeInTheDocument();

    act(() =>
      currentSource().emitMessage({
        timestamp: '2026-07-18T09:30:05Z',
        jvm: baseSample.jvm,
      }),
    );

    expect(screen.getAllByText('50 %').length).toBeGreaterThan(0);
    expect(screen.getAllByText('42').length).toBeGreaterThan(0);
    expect(screen.getAllByText(t.noValue).length).toBeGreaterThan(0);
  });
});
