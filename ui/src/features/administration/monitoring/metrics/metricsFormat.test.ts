import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  deltaOf,
  errorRate,
  errorRateSeverity,
  formatDecimal,
  formatMb,
  formatPercent,
  formatSingleMb,
  formatUptime,
  fractionToPercent,
  heapUsageMb,
  heapUsedPercent,
  peakOf,
  systemLoadPercent,
} from './metricsFormat';
import type { RuntimeMetrics } from './metricsStream';

function sample(overrides: Partial<RuntimeMetrics> = {}): RuntimeMetrics {
  return { timestamp: '2026-07-18T09:30:00Z', ...overrides };
}

describe('formatPercent', () => {
  it('converts a 0-1 fraction into a rounded percentage string', () => {
    expect(formatPercent(0.5)).toBe('50 %');
  });

  it('rounds to the nearest whole percent', () => {
    expect(formatPercent(0.256)).toBe('26 %');
  });

  it('formats a zero fraction as 0 %', () => {
    expect(formatPercent(0)).toBe('0 %');
  });
});

describe('formatDecimal', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('formats with two decimal digits by default, using the gl-ES comma separator', () => {
    expect(formatDecimal(4.2)).toBe('4,20');
  });

  it('rounds to the requested number of fraction digits', () => {
    expect(formatDecimal(4.256)).toBe('4,26');
  });

  it('honours a custom fractionDigits argument', () => {
    expect(formatDecimal(4.2, 0)).toBe('4');
  });

  it('formats zero with the requested precision', () => {
    expect(formatDecimal(0)).toBe('0,00');
  });

  it('groups thousands with a space, matching formatCount, not a dot', () => {
    expect(formatDecimal(1234.5)).toBe('1 234,50');
  });

  it('keeps the Galician separators when the runtime has no gl-ES data to resolve', () => {
    // Node ships full ICU and formats gl-ES as "1.234,50"; a browser build that
    // lacks it resolves to the "1,234.50" shape instead — the two disagree about
    // which of '.' and ',' is the decimal mark. Standing in for such a build is
    // the only way to see that from here, and headless Chromium is one: it
    // rendered 1,90 s as "1 90 s" until this was fixed.
    const RealNumberFormat = Intl.NumberFormat;
    class FallbackNumberFormat {
      readonly formatToParts: (value: number) => Intl.NumberFormatPart[];
      constructor(_locales: unknown, options: Intl.NumberFormatOptions) {
        const delegate = new RealNumberFormat('en-US', options);
        this.formatToParts = (value) => delegate.formatToParts(value);
      }
    }
    vi.spyOn(Intl, 'NumberFormat').mockImplementation(
      FallbackNumberFormat as unknown as typeof Intl.NumberFormat,
    );

    expect(formatDecimal(1234.5)).toBe('1 234,50');
    expect(formatDecimal(1.902)).toBe('1,90');
    expect(formatDecimal(-4.2)).toBe('-4,20');
  });
});

describe('formatMb', () => {
  it('renders both used and max bytes converted to whole megabytes', () => {
    expect(formatMb(536_870_912, 1_073_741_824)).toBe('512 / 1024 MB');
  });

  it('rounds each value to the nearest megabyte independently', () => {
    expect(formatMb(1_500_000, 3_000_000)).toBe('1 / 3 MB');
  });
});

describe('formatSingleMb', () => {
  it('converts bytes to a rounded whole-megabyte string', () => {
    expect(formatSingleMb(100_663_296)).toBe('96 MB');
  });
});

describe('formatUptime', () => {
  it('renders days, hours and minutes, zero-padding hours and minutes', () => {
    expect(formatUptime(266_320_000)).toBe('3 d 01 h 58 m');
  });

  it('renders a duration under one day as 0 days', () => {
    expect(formatUptime(3_600_000)).toBe('0 d 01 h 00 m');
  });

  it('rounds down to the whole minute, discarding leftover seconds', () => {
    expect(formatUptime(59_000)).toBe('0 d 00 h 00 m');
  });
});

describe('heapUsedPercent', () => {
  it('returns the ratio of used to max heap bytes', () => {
    const s = sample({ jvm: { heapUsedBytes: 512, heapMaxBytes: 1024 } });

    expect(heapUsedPercent(s)).toBe(0.5);
  });

  it('returns null when jvm is absent', () => {
    expect(heapUsedPercent(sample())).toBeNull();
  });

  it('returns null when heapUsedBytes is missing', () => {
    const s = sample({ jvm: { heapMaxBytes: 1024 } });

    expect(heapUsedPercent(s)).toBeNull();
  });

  it('returns null when heapMaxBytes is missing', () => {
    const s = sample({ jvm: { heapUsedBytes: 512 } });

    expect(heapUsedPercent(s)).toBeNull();
  });

  it('returns null instead of dividing by a zero heapMaxBytes', () => {
    const s = sample({ jvm: { heapUsedBytes: 512, heapMaxBytes: 0 } });

    expect(heapUsedPercent(s)).toBeNull();
  });
});

describe('heapUsageMb', () => {
  it('returns null when the sample itself is null', () => {
    expect(heapUsageMb(null)).toBeNull();
  });

  it('returns null when jvm is absent', () => {
    expect(heapUsageMb(sample())).toBeNull();
  });

  it('returns null when heapMaxBytes is missing', () => {
    const s = sample({ jvm: { heapUsedBytes: 536_870_912 } });

    expect(heapUsageMb(s)).toBeNull();
  });

  it('returns null when heapUsedBytes is missing', () => {
    const s = sample({ jvm: { heapMaxBytes: 1_073_741_824 } });

    expect(heapUsageMb(s)).toBeNull();
  });

  it('formats both values into an MB string when both are present', () => {
    const s = sample({ jvm: { heapUsedBytes: 536_870_912, heapMaxBytes: 1_073_741_824 } });

    expect(heapUsageMb(s)).toBe('512 / 1024 MB');
  });
});

describe('systemLoadPercent', () => {
  it('returns the raw cpuLoad value', () => {
    const s = sample({ system: { cpuLoad: 0.35 } });

    expect(systemLoadPercent(s)).toBe(0.35);
  });

  it('returns null when system is absent', () => {
    expect(systemLoadPercent(sample())).toBeNull();
  });

  it('returns null when cpuLoad is explicitly null', () => {
    const s = sample({ system: { cpuLoad: null } });

    expect(systemLoadPercent(s)).toBeNull();
  });
});

describe('fractionToPercent', () => {
  it('scales a 0-1 fraction to a 0-100 value', () => {
    expect(fractionToPercent(0.35)).toBe(35);
  });

  it('returns null when given null', () => {
    expect(fractionToPercent(null)).toBeNull();
  });
});

describe('errorRate', () => {
  it('returns the ratio of errors to requests', () => {
    expect(errorRate(100, 5)).toBe(0.05);
  });

  it('returns null when requestCount is missing', () => {
    expect(errorRate(undefined, 5)).toBeNull();
  });

  it('returns null when errorCount is missing', () => {
    expect(errorRate(100, undefined)).toBeNull();
  });

  it('returns null instead of dividing by a zero requestCount', () => {
    expect(errorRate(0, 0)).toBeNull();
  });
});

describe('errorRateSeverity', () => {
  it('classifies a rate just below 1% as normal', () => {
    expect(errorRateSeverity(0.0099)).toBe('normal');
  });

  it('classifies exactly 1% as elevated, not normal', () => {
    expect(errorRateSeverity(0.01)).toBe('elevated');
  });

  it('classifies a rate just below 5% as elevated', () => {
    expect(errorRateSeverity(0.049)).toBe('elevated');
  });

  it('classifies exactly 5% as high, not elevated', () => {
    expect(errorRateSeverity(0.05)).toBe('high');
  });

  it('classifies a rate above 5% as high', () => {
    expect(errorRateSeverity(0.5)).toBe('high');
  });
});

describe('peakOf', () => {
  it('returns the maximum of the selected values across the history', () => {
    const history = [sample(), sample(), sample()];
    const values = [10, 30, 20];
    let index = 0;

    expect(peakOf(history, () => values[index++])).toBe(30);
  });

  it('returns null for an empty history', () => {
    expect(peakOf([], () => 1)).toBeNull();
  });

  it('returns null when every sample selects to null', () => {
    const history = [sample(), sample()];

    expect(peakOf(history, () => null)).toBeNull();
  });

  it('ignores samples where the selector returns null', () => {
    const history = [sample(), sample(), sample()];
    const values: (number | null)[] = [10, null, 20];
    let index = 0;

    expect(peakOf(history, () => values[index++])).toBe(20);
  });
});

describe('deltaOf', () => {
  it('returns the difference between the last two selected values', () => {
    const history = [sample(), sample(), sample()];
    const values = [10, 20, 35];
    let index = 0;

    expect(deltaOf(history, () => values[index++])).toBe(15);
  });

  it('returns a negative difference when the value decreased', () => {
    const history = [sample(), sample()];
    const values = [35, 20];
    let index = 0;

    expect(deltaOf(history, () => values[index++])).toBe(-15);
  });

  it('returns null for an empty history', () => {
    expect(deltaOf([], () => 1)).toBeNull();
  });

  it('returns null when only one selected value is present', () => {
    const history = [sample()];

    expect(deltaOf(history, () => 5)).toBeNull();
  });

  it('skips samples where the selector returns null, diffing across the gap', () => {
    const history = [sample(), sample(), sample()];
    const values: (number | null)[] = [5, null, 8];
    let index = 0;

    expect(deltaOf(history, () => values[index++])).toBe(3);
  });
});
