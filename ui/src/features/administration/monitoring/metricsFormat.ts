import type { RuntimeMetrics } from './metricsStream';

export function formatCount(n: number): string {
  return Math.round(n)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
}

export function formatPercent(fraction: number): string {
  return `${Math.round(fraction * 100)} %`;
}

export function formatDecimal(n: number, fractionDigits = 2): string {
  return new Intl.NumberFormat('gl-ES', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(n);
}

export function formatTime(date: Date): string {
  return date.toLocaleTimeString('gl-ES', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  });
}

export function formatMb(usedBytes: number, maxBytes: number): string {
  const toMb = (bytes: number) => Math.round(bytes / (1024 * 1024));
  return `${toMb(usedBytes)} / ${toMb(maxBytes)} MB`;
}

export function formatSingleMb(bytes: number): string {
  return `${Math.round(bytes / (1024 * 1024))} MB`;
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

export function systemLoadPercent(sample: RuntimeMetrics): number | null {
  return sample.system?.cpuLoad ?? null;
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

export function peakOf(
  history: RuntimeMetrics[],
  select: (sample: RuntimeMetrics) => number | null,
): number | null {
  const values = history.map(select).filter((value): value is number => value != null);
  return values.length > 0 ? Math.max(...values) : null;
}

export function deltaOf(
  history: RuntimeMetrics[],
  select: (sample: RuntimeMetrics) => number | null,
): number | null {
  const values = history.map(select).filter((value): value is number => value != null);
  if (values.length < 2) {
    return null;
  }
  return values[values.length - 1] - values[values.length - 2];
}
