import type { RuntimeMetrics } from './metricsStream';

// The backtracking sonarjs warns about is bounded here: this only ever matches
// against a rounded Number's own decimal form, never longer than ~21 characters.
// eslint-disable-next-line sonarjs/super-linear-regex
const thousandsBoundary = /\B(?=(\d{3})+(?!\d))/g;

export function formatCount(n: number): string {
  return Math.round(n).toString().replace(thousandsBoundary, ' ');
}

export function formatPercent(fraction: number): string {
  return `${Math.round(fraction * 100)} %`;
}

export function formatDecimal(n: number, fractionDigits = 2): string {
  // Grouping is a space, to match formatCount above; the decimal mark is the
  // comma Galician writes. Both are taken from the formatter's own parts rather
  // than patched into its output: which character plays which role depends on
  // the locale data the runtime actually resolves for gl-ES, and a build that
  // falls back to '.' as the decimal separator would have had that separator
  // rewritten as a space — 1,90 rendered as "1 90".
  return new Intl.NumberFormat('gl-ES', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })
    .formatToParts(n)
    .map((part) => {
      if (part.type === 'group') {
        return ' ';
      }
      return part.type === 'decimal' ? ',' : part.value;
    })
    .join('');
}

export function formatTime(date: Date): string {
  return date.toLocaleTimeString('gl-ES', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  });
}

function bytesToMb(bytes: number): number {
  return Math.round(bytes / (1024 * 1024));
}

export function formatMb(usedBytes: number, maxBytes: number): string {
  return `${bytesToMb(usedBytes)} / ${bytesToMb(maxBytes)} MB`;
}

export function formatSingleMb(bytes: number): string {
  return `${bytesToMb(bytes)} MB`;
}

export function formatUptime(uptimeMillis: number): string {
  const totalMinutes = Math.floor(uptimeMillis / 60_000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  return `${days} d ${String(hours).padStart(2, '0')} h ${String(minutes).padStart(2, '0')} m`;
}

function safeDivide(numerator?: number | null, denominator?: number | null): number | null {
  if (numerator == null || denominator == null || denominator === 0) {
    return null;
  }
  return numerator / denominator;
}

export function heapUsedPercent(sample: RuntimeMetrics): number | null {
  return safeDivide(sample.jvm?.heapUsedBytes, sample.jvm?.heapMaxBytes);
}

export function heapUsageMb(sample: RuntimeMetrics | null): string | null {
  if (sample?.jvm?.heapUsedBytes == null || sample.jvm.heapMaxBytes == null) {
    return null;
  }
  return formatMb(sample.jvm.heapUsedBytes, sample.jvm.heapMaxBytes);
}

export function systemLoadPercent(sample: RuntimeMetrics): number | null {
  return sample.system?.cpuLoad ?? null;
}

export function fractionToPercent(fraction: number | null): number | null {
  return fraction != null ? fraction * 100 : null;
}

export function errorRate(requestCount?: number, errorCount?: number): number | null {
  return safeDivide(errorCount, requestCount);
}

export type ErrorRateSeverity = 'normal' | 'elevated' | 'high';

export function errorRateSeverity(rate: number): ErrorRateSeverity {
  if (rate < 0.01) {
    return 'normal';
  }
  if (rate < 0.05) {
    return 'elevated';
  }
  return 'high';
}

function selectNumericValues(
  history: RuntimeMetrics[],
  select: (sample: RuntimeMetrics) => number | null,
): number[] {
  return history.map(select).filter((value): value is number => value != null);
}

export function peakOf(
  history: RuntimeMetrics[],
  select: (sample: RuntimeMetrics) => number | null,
): number | null {
  const values = selectNumericValues(history, select);
  return values.length > 0 ? Math.max(...values) : null;
}

export function deltaOf(
  history: RuntimeMetrics[],
  select: (sample: RuntimeMetrics) => number | null,
): number | null {
  const values = selectNumericValues(history, select);
  if (values.length < 2) {
    return null;
  }
  return values[values.length - 1] - values[values.length - 2];
}
