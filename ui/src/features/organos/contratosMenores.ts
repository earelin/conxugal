import { useMutation, useQuery } from '@tanstack/react-query';

import { apiFetch } from '../../shared/lib/httpClient';
import { ORGANOS_QUERY_KEY, useOrganosMutation } from './organos';

const IMPORT_PATH = '/api/admin/contratos-menores/import';
const RUN_PATH = '/api/admin/import-run';

function markPath(organoId: string): string {
  return `/api/admin/organo/${organoId}/importable`;
}

/**
 * Why an import the caller asked for never started. The same two reasons reach
 * the UI by two routes — an enum on the mark's `200`, a problem type on a
 * trigger's `409` — and both are normal answers rather than failures.
 */
export type ImportRefusalKind = 'IMPORT_ALREADY_RUNNING' | 'ORGANO_NOT_ELIGIBLE';

/**
 * What marking an Órgano did. The mark is always written; exactly one of these
 * two says what became of the import it asked for.
 */
export interface MarkOutcome {
  runId: string | null;
  refusal: ImportRefusalKind | null;
}

export type ImportRunState =
  'IN_PROGRESS' | 'SUCCEEDED' | 'PARTIALLY_SUCCEEDED' | 'FAILED' | 'ABANDONED';

export type ImportRunOrganoState =
  'PENDING' | 'IN_PROGRESS' | 'SUCCEEDED' | 'FAILED' | 'STOPPED' | 'SKIPPED';

export interface ImportRunOrgano {
  organoId: string;
  state: ImportRunOrganoState;
  added: number;
  refreshed: number;
  failureReason: string | null;
}

export interface ImportRun {
  id: string;
  importer: 'ORGANOS' | 'CONTRATOS_MENORES';
  state: ImportRunState;
  startedAt: string;
  /** Null while a run is going, and for an abandoned one, which never ended. */
  finishedAt: string | null;
  added: number;
  refreshed: number;
  /** Enumerated when the run was claimed, not as the run reached them. */
  coveredOrganos: ImportRunOrgano[];
}

export const IMPORT_RUN_QUERY_KEY = ['import-run'] as const;

async function mark(organoId: string): Promise<MarkOutcome> {
  const response = await apiFetch(markPath(organoId), { method: 'PUT' });
  return response.json() as Promise<MarkOutcome>;
}

async function unmark(organoId: string): Promise<void> {
  await apiFetch(markPath(organoId), { method: 'DELETE' });
}

async function startImport(): Promise<string> {
  const response = await apiFetch(IMPORT_PATH, { method: 'POST' });
  const started = (await response.json()) as { runId: string };
  return started.runId;
}

async function fetchRun(runId: string): Promise<ImportRun> {
  const response = await apiFetch(`${RUN_PATH}/${runId}`);
  return response.json() as Promise<ImportRun>;
}

/**
 * Marks an Órgano and reports what the import it asked for did.
 *
 * A refused import still leaves the mark written — the server answers `200` for
 * exactly that reason — so the catalogue is re-read either way.
 */
export function useMarkOrgano() {
  return useOrganosMutation(mark, ORGANOS_QUERY_KEY);
}

export function useUnmarkOrgano() {
  return useOrganosMutation(unmark, ORGANOS_QUERY_KEY);
}

/**
 * Asks for an import of every marked, active Órgano. Nothing is re-read on
 * success: the trigger answers before the import has done anything, so the
 * catalogue it will eventually change is still exactly what is on screen.
 */
export function useStartContratosMenoresImport() {
  return useMutation({ mutationFn: startImport });
}

/**
 * One run, read on demand. There is no polling and no scheduled refetch: the
 * banner says when it last asked, and re-asking is the reader's decision.
 */
export function useImportRun(runId: string | null) {
  return useQuery({
    queryKey: [...IMPORT_RUN_QUERY_KEY, runId],
    queryFn: () => fetchRun(runId as string),
    enabled: runId !== null,
  });
}
