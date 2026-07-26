import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../../shared/lib/httpClient';

export type ServiceStatus = 'UP' | 'DEGRADED';

export interface SystemStatus {
  status: ServiceStatus;
  datastore: { reachable: boolean };
  checkedAt: string;
  application: { version: string; environment: string };
  runtime: {
    javaVersion: string;
    javaVendor: string;
    uptimeMillis: number;
    memoryUsedBytes: number;
    memoryMaxBytes: number;
    osName: string;
    osArch: string;
  };
}

export const SYSTEM_STATUS_QUERY_KEY = ['systemStatus'] as const;

async function fetchSystemStatus(): Promise<SystemStatus> {
  const response = await apiFetch('/api/admin/system-status');
  return response.json() as Promise<SystemStatus>;
}

/**
 * `staleTime`/`gcTime` of 0 mean a dependency-state change (e.g. the datastore
 * going unreachable) is always reflected on the next dashboard view, never
 * masked by a cached healthy snapshot (SPEC-0003 #3).
 */
export function useSystemStatus() {
  return useQuery({
    queryKey: SYSTEM_STATUS_QUERY_KEY,
    queryFn: fetchSystemStatus,
    staleTime: 0,
    gcTime: 0,
    refetchOnMount: 'always',
  });
}
