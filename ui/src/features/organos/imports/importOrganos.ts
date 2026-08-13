import { useMutation, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '../../../shared/lib/httpClient';
import { ORGANOS_QUERY_KEY } from '../organos';

const IMPORT_PATH = '/api/admin/organos/import';

/**
 * The two outcomes an import that ran can report. A source failure is not one
 * of them: the server raises it as a 500 carrying its own problem type, so it
 * reaches this module as a thrown `ProblemError` and can never be read as a
 * success with three zeroes.
 */
export interface ImportOutcome {
  status: 'SUCCESS' | 'ALREADY_RUNNING';
  added: number;
  refreshed: number;
  deactivated: number;
}

async function runImport(): Promise<ImportOutcome> {
  const response = await apiFetch(IMPORT_PATH, { method: 'POST' });
  return response.json() as Promise<ImportOutcome>;
}

/**
 * Triggers an import and refetches the catalogue it changed — re-running the
 * builder over it is what lands newly imported Órganos in the worklist without
 * a manual reload. The taxonomía is untouched by an import, so it is not
 * refetched.
 *
 * This does not go through `useOrganosMutation`, which invalidates on every
 * success: `ALREADY_RUNNING` is a 200 that wrote nothing, and refetching for it
 * would spend two requests to redraw the same list.
 */
export function useImportOrganos() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: runImport,
    onSuccess: (outcome) => {
      if (outcome.status === 'SUCCESS') {
        // Deliberately not awaited. Returning the invalidation would hold the
        // mutation pending until the catalogue re-read settled — including its
        // retries — so the button would go on saying "Importando…" after the
        // import had answered. The outcome is known; the list can catch up.
        void queryClient.invalidateQueries({ queryKey: ORGANOS_QUERY_KEY });
      }
    },
  });
}
