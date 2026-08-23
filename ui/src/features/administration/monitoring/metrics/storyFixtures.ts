import { METRICS_HISTORY_LIMIT, type RuntimeMetrics } from './metricsStream';

/**
 * Samples shaped like the ones the SSE stream delivers, shared by the metrics
 * stories so every tile and card on the page is reading one machine.
 *
 * Nothing here is random: a story that redrew different numbers on every
 * refresh would make the tiles impossible to compare against each other.
 */

const MB = 1024 * 1024;

/** The cadence the panel's own copy tells the reader to expect. */
const SAMPLE_INTERVAL_MS = 5_000;

const HEAP_MAX = 512 * MB;

/**
 * One sample, `step` places into a run. The figures wander a little so the
 * sparklines have a shape, and the counters only ever climb, as the real
 * accumulating ones do.
 */
export function sampleAt(step: number): RuntimeMetrics {
  const wave = Math.sin(step / 7);
  return {
    timestamp: new Date(Date.UTC(2026, 7, 23, 9, 0, 0) + step * SAMPLE_INTERVAL_MS).toISOString(),
    jvm: {
      heapUsedBytes: Math.round((190 + wave * 45) * MB),
      heapMaxBytes: HEAP_MAX,
      nonHeapUsedBytes: Math.round((78 + wave * 3) * MB),
      threadCount: 42 + Math.round(wave * 6),
      uptimeMillis: 3_600_000 * 26 + step * SAMPLE_INTERVAL_MS,
      gcCollectionCount: 118 + step,
      gcCollectionTimeMillis: 2_140 + step * 11,
    },
    system: { cpuLoad: 0.18 + wave * 0.09 },
    http: { requestCount: 48_210 + step * 37, errorCount: 96 + Math.round(step / 9) },
    datastorePool: { active: 3 + Math.round(Math.abs(wave) * 2), idle: 3, max: 10 },
  };
}

/** A full history buffer, which is what makes the tiles show their peaks. */
export const HISTORY: RuntimeMetrics[] = Array.from({ length: METRICS_HISTORY_LIMIT }, (_, step) =>
  sampleAt(step),
);

export const LATEST: RuntimeMetrics = HISTORY[HISTORY.length - 1];

/** Barely into a run, so the tiles caption the samples they still lack. */
export const SHORT_HISTORY: RuntimeMetrics[] = HISTORY.slice(0, 8);

/**
 * A server that answers but reports nothing this panel can read — every field
 * the cards want is absent, which is the case `orNoValue` exists for.
 */
export const EMPTY_SAMPLE: RuntimeMetrics = { timestamp: LATEST.timestamp };
