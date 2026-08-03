import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { theme } from '../../../../app/theme';
import { strings } from '../../../../shared/lib/strings';
import { METRICS_HISTORY_LIMIT, type RuntimeMetrics } from './metricsStream';
import { HeapTile, HttpTile, SystemLoadTile, ThreadsTile } from './MetricTiles';

const t = strings.admin.dashboard.metrics;

// A `getByText`/`queryByText` matcher for "contains this substring", without
// building a RegExp out of Galician copy — some copy (e.g. "máx.") contains
// characters ('.') that are regex metacharacters, which would silently
// loosen the match.
function containingText(substring: string) {
  return (content: string) => content.includes(substring);
}

function renderTile(node: React.ReactElement) {
  return render(<MantineProvider theme={theme}>{node}</MantineProvider>);
}

const baseSample: RuntimeMetrics = {
  timestamp: '2026-07-18T09:30:00Z',
  jvm: {
    heapUsedBytes: 536_870_912,
    heapMaxBytes: 1_073_741_824,
    threadCount: 42,
  },
  system: { cpuLoad: 0.35 },
  http: { requestCount: 15_230 },
};

function historyOf(
  count: number,
  overrideAt: Partial<Record<number, Partial<RuntimeMetrics>>> = {},
) {
  return Array.from({ length: count }, (_, i) => ({
    ...baseSample,
    timestamp: `2026-07-18T09:30:${String(i).padStart(2, '0')}Z`,
    ...overrideAt[i],
  }));
}

describe('HeapTile', () => {
  it('shows only the label while connecting, never a fabricated value', () => {
    renderTile(<HeapTile state="connecting" latest={null} history={[]} />);

    expect(screen.getByText(t.heapTileLabel)).toBeInTheDocument();
    expect(screen.queryByText(t.noValue)).not.toBeInTheDocument();
    expect(screen.queryByText(containingText('MB'))).not.toBeInTheDocument();
  });

  it('shows the percentage, MB usage, and sample count while filling the history window', () => {
    const history = historyOf(3);
    renderTile(<HeapTile state="live" latest={baseSample} history={history} />);

    expect(screen.getByText('50 %')).toBeInTheDocument();
    expect(
      screen.getByText(
        containingText(`512 / 1024 MB · 3/${METRICS_HISTORY_LIMIT} ${t.samplesUnit}`),
      ),
    ).toBeInTheDocument();
  });

  it('shows the peak percentage once the history window is full', () => {
    const history = historyOf(METRICS_HISTORY_LIMIT, {
      0: { jvm: { ...baseSample.jvm, heapUsedBytes: 1_020_054_733 } }, // ~95%
    });
    renderTile(<HeapTile state="live" latest={baseSample} history={history} />);

    expect(screen.getByText(containingText(`${t.peaksOfHeapPrefix} 95 %`))).toBeInTheDocument();
  });

  it('shows the last known value dimmed with a not-updating caption while reconnecting', () => {
    renderTile(<HeapTile state="reconnecting" latest={baseSample} history={historyOf(5)} />);

    expect(screen.getByText('50 %')).toBeInTheDocument();
    expect(screen.getByText(containingText(t.notUpdating))).toBeInTheDocument();
  });

  it('shows a dash instead of a fabricated percentage when heap data is absent', () => {
    const sample: RuntimeMetrics = { timestamp: '2026-07-18T09:30:00Z' };
    renderTile(<HeapTile state="live" latest={sample} history={[sample]} />);

    expect(screen.getByText(t.noValue)).toBeInTheDocument();
    expect(
      screen.getByText(containingText(`1/${METRICS_HISTORY_LIMIT} ${t.samplesUnit}`)),
    ).toBeInTheDocument();
  });
});

describe('SystemLoadTile', () => {
  it('shows the recent-load caption with sample count while filling the history window', () => {
    const history = historyOf(3);
    renderTile(<SystemLoadTile state="live" latest={baseSample} history={history} />);

    expect(screen.getByText('35 %')).toBeInTheDocument();
    expect(
      screen.getByText(
        containingText(`${t.recentLoadPrefix} · 3/${METRICS_HISTORY_LIMIT} ${t.samplesUnit}`),
      ),
    ).toBeInTheDocument();
  });

  it('shows the peak load percentage once the history window is full', () => {
    const history = historyOf(METRICS_HISTORY_LIMIT, { 0: { system: { cpuLoad: 0.95 } } });
    renderTile(<SystemLoadTile state="live" latest={baseSample} history={history} />);

    expect(screen.getByText(containingText(`${t.maxOfLoadPrefix} 95 %`))).toBeInTheDocument();
    expect(screen.queryByText(containingText(`${t.maxOfLoadPrefix} 35 %`))).not.toBeInTheDocument();
  });

  it('shows the last-known-load caption while reconnecting', () => {
    renderTile(<SystemLoadTile state="reconnecting" latest={baseSample} history={historyOf(5)} />);

    expect(screen.getByText(t.lastKnownLoad)).toBeInTheDocument();
  });
});

describe('ThreadsTile', () => {
  it('shows the peak thread count as a plain integer, not a percentage', () => {
    const history = historyOf(METRICS_HISTORY_LIMIT, {
      0: { jvm: { ...baseSample.jvm, threadCount: 999 } },
    });
    renderTile(<ThreadsTile state="live" latest={baseSample} history={history} />);

    expect(screen.getByText(containingText(`${t.peakOfThreadsPrefix} 999`))).toBeInTheDocument();
    expect(
      screen.queryByText(containingText(`${t.peakOfThreadsPrefix} 999 %`)),
    ).not.toBeInTheDocument();
  });

  it('shows the alive-now caption with sample count while filling the history window', () => {
    const history = historyOf(3);
    renderTile(<ThreadsTile state="live" latest={baseSample} history={history} />);

    expect(screen.getByText('42')).toBeInTheDocument();
    expect(
      screen.getByText(
        containingText(`${t.aliveNowPrefix} · 3/${METRICS_HISTORY_LIMIT} ${t.samplesUnit}`),
      ),
    ).toBeInTheDocument();
  });

  it('shows the last-known-threads caption while reconnecting', () => {
    renderTile(<ThreadsTile state="reconnecting" latest={baseSample} history={historyOf(5)} />);

    expect(screen.getByText(t.lastKnownThreads)).toBeInTheDocument();
  });
});

describe('HttpTile', () => {
  it('shows the delta since the last sample with a plus sign for an increase', () => {
    const history = [
      { ...baseSample, http: { requestCount: 15_000 } },
      { ...baseSample, http: { requestCount: 15_230 } },
    ];
    renderTile(<HttpTile state="live" latest={history[1]} history={history} />);

    expect(screen.getByText(`+230 ${t.sinceLastSamplePrefix}`)).toBeInTheDocument();
  });

  it('shows the delta since the last sample with a minus sign for a decrease', () => {
    const history = [
      { ...baseSample, http: { requestCount: 15_230 } },
      { ...baseSample, http: { requestCount: 15_000 } },
    ];
    renderTile(<HttpTile state="live" latest={history[1]} history={history} />);

    expect(screen.getByText(`-230 ${t.sinceLastSamplePrefix}`)).toBeInTheDocument();
  });

  it('shows noHistoryYet when there is not yet a previous sample to diff against', () => {
    renderTile(<HttpTile state="live" latest={baseSample} history={[baseSample]} />);

    expect(screen.getByText(t.noHistoryYet)).toBeInTheDocument();
  });

  it('shows the no-new-samples caption while reconnecting, ignoring any delta', () => {
    const history = [
      { ...baseSample, http: { requestCount: 15_000 } },
      { ...baseSample, http: { requestCount: 15_230 } },
    ];
    renderTile(<HttpTile state="reconnecting" latest={history[1]} history={history} />);

    expect(screen.getByText(t.noNewSamples)).toBeInTheDocument();
    expect(screen.queryByText(containingText(t.sinceLastSamplePrefix))).not.toBeInTheDocument();
  });
});
