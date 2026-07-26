import { useEffect, useState } from 'react';

export interface RuntimeMetrics {
  timestamp: string;
  jvm?: {
    heapUsedBytes?: number;
    heapMaxBytes?: number;
    nonHeapUsedBytes?: number;
    threadCount?: number;
    uptimeMillis?: number;
    gcCollectionCount?: number;
    gcCollectionTimeMillis?: number;
  };
  system?: {
    cpuLoad?: number | null;
  };
  http?: {
    requestCount?: number;
    errorCount?: number;
  };
  datastorePool?: {
    active?: number;
    idle?: number;
    max?: number;
  };
}

export type MetricsStreamState = 'connecting' | 'live' | 'reconnecting';

export interface MetricsStreamResult {
  state: MetricsStreamState;
  latest: RuntimeMetrics | null;
  history: RuntimeMetrics[];
  lastArrivedAt: Date | null;
}

export const METRICS_STREAM_URL = '/api/admin/metrics';
export const METRICS_HISTORY_LIMIT = 30;

/**
 * Drives the admin metrics panel from the `/api/admin/metrics` SSE stream. Relies
 * entirely on `EventSource`'s native reconnection (ADR-0009): the only two state
 * transitions are "a sample arrived" (live) and "the connection dropped" (connecting,
 * if no sample has arrived yet in this mount, otherwise reconnecting) — never a
 * separate error state, since a dropped stream keeps retrying on its own.
 */
export function useMetricsStream(url: string = METRICS_STREAM_URL): MetricsStreamResult {
  const [state, setState] = useState<MetricsStreamState>('connecting');
  const [latest, setLatest] = useState<RuntimeMetrics | null>(null);
  const [history, setHistory] = useState<RuntimeMetrics[]>([]);
  const [lastArrivedAt, setLastArrivedAt] = useState<Date | null>(null);

  useEffect(() => {
    let hasReceivedSample = false;
    const source = new EventSource(url);

    source.onmessage = (event: MessageEvent<string>) => {
      let sample: RuntimeMetrics;
      try {
        sample = JSON.parse(event.data) as RuntimeMetrics;
      } catch {
        return;
      }
      hasReceivedSample = true;
      setLatest(sample);
      setLastArrivedAt(new Date());
      setState('live');
      setHistory((previous) => {
        const next = [...previous, sample];
        return next.length > METRICS_HISTORY_LIMIT
          ? next.slice(next.length - METRICS_HISTORY_LIMIT)
          : next;
      });
    };

    source.onerror = () => {
      setState(hasReceivedSample ? 'reconnecting' : 'connecting');
    };

    return () => source.close();
  }, [url]);

  return { state, latest, history, lastArrivedAt };
}
