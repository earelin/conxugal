import { MantineProvider } from '@mantine/core';
import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { theme } from '../../../../app/theme';
import { strings } from '../../../../shared/lib/strings';
import { formatTime } from './metricsFormat';
import { MetricsSectionHeader } from './MetricsSectionHeader';
import type { MetricsStreamState } from './metricsStream';

const t = strings.admin.dashboard.metrics;

function renderHeader(state: MetricsStreamState, lastArrivedAt: Date | null) {
  return render(
    <MantineProvider theme={theme}>
      <MetricsSectionHeader state={state} lastArrivedAt={lastArrivedAt} />
    </MantineProvider>,
  );
}

function liveIndicator(container: HTMLElement) {
  return container.querySelector('[data-processing="true"]');
}

describe('MetricsSectionHeader', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows a gray connecting badge and the awaiting-first-sample caption while connecting', () => {
    const { container } = renderHeader('connecting', null);

    expect(screen.getByText(t.stateConnecting)).toBeInTheDocument();
    expect(screen.getByText(t.awaitingFirstSample)).toBeInTheDocument();
    expect(liveIndicator(container)).not.toBeInTheDocument();
  });

  it('shows a live badge with the last-sample time, cadence caption, and pulsing indicator', () => {
    const lastArrivedAt = new Date('2026-07-18T09:30:00Z');
    const { container } = renderHeader('live', lastArrivedAt);

    expect(screen.getByText(t.stateLive)).toBeInTheDocument();
    expect(
      screen.getByText(`${t.lastSampleAtPrefix} ${formatTime(lastArrivedAt)} · ${t.cadenceSuffix}`),
    ).toBeInTheDocument();
    expect(liveIndicator(container)).toBeInTheDocument();
  });

  it('shows the pulsing indicator in the live state even when no sample time is available yet, unlike the caption', () => {
    const { container } = renderHeader('live', null);

    expect(screen.getByText(t.stateLive)).toBeInTheDocument();
    expect(screen.getByText(t.awaitingFirstSample)).toBeInTheDocument();
    expect(liveIndicator(container)).toBeInTheDocument();
  });

  it('shows a yellow reconnecting badge with the last-known time and zero elapsed seconds right after dropping', () => {
    const lastArrivedAt = new Date('2026-07-18T09:30:00Z');
    vi.useFakeTimers();
    vi.setSystemTime(lastArrivedAt);

    const { container } = renderHeader('reconnecting', lastArrivedAt);

    expect(screen.getByText(t.stateReconnecting)).toBeInTheDocument();
    expect(
      screen.getByText(
        `${t.lastKnownSampleAtPrefix} ${formatTime(lastArrivedAt)} · ${t.timeAgoPrefix} 0 ${t.timeAgoUnit}`,
      ),
    ).toBeInTheDocument();
    expect(liveIndicator(container)).not.toBeInTheDocument();
  });

  it('increases the elapsed-seconds caption every second while reconnecting', () => {
    const lastArrivedAt = new Date('2026-07-18T09:30:00Z');
    vi.useFakeTimers();
    vi.setSystemTime(lastArrivedAt);

    renderHeader('reconnecting', lastArrivedAt);

    act(() => {
      vi.advanceTimersByTime(5_000);
    });

    expect(
      screen.getByText(
        `${t.lastKnownSampleAtPrefix} ${formatTime(lastArrivedAt)} · ${t.timeAgoPrefix} 5 ${t.timeAgoUnit}`,
      ),
    ).toBeInTheDocument();
  });

  it('falls back to the awaiting-first-sample caption while reconnecting when no sample time is available', () => {
    renderHeader('reconnecting', null);

    expect(screen.getByText(t.stateReconnecting)).toBeInTheDocument();
    expect(screen.getByText(t.awaitingFirstSample)).toBeInTheDocument();
  });

  it('exposes the state caption and badge as an accessible polite live region', () => {
    renderHeader('live', new Date('2026-07-18T09:30:00Z'));

    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
  });
});
