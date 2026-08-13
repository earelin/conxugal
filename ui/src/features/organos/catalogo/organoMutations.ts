import { apiFetch } from '../../../shared/lib/httpClient';
import { ORGANOS_QUERY_KEY, useOrganosMutation } from '../organos';

const ORGANO_PATH = '/api/admin/organo';

export interface PlaceOrganoInput {
  organoId: string;
  /**
   * The term to file the Órgano in. Never null: leaving an Órgano in no term is
   * the clear, a different operation with different rules.
   */
  termoId: string;
}

async function placeOrgano({ organoId, termoId }: PlaceOrganoInput): Promise<void> {
  await apiFetch(`${ORGANO_PATH}/${organoId}/termo`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ termoId }),
  });
}

async function clearOrgano(organoId: string): Promise<void> {
  await apiFetch(`${ORGANO_PATH}/${organoId}/termo`, { method: 'DELETE' });
}

// Both writes touch one field of one Órgano. The taxonomía itself is unchanged
// by either, so only the catalogue is refetched — and re-running the builder
// over it is what moves the Órgano between the worklist and a term's table.

/** Files an Órgano in a term, replacing any placement it already had. */
export function usePlaceOrgano() {
  return useOrganosMutation(placeOrgano, ORGANOS_QUERY_KEY);
}

/** Returns an Órgano to the unclassified worklist. Never deletes it. */
export function useClearOrgano() {
  return useOrganosMutation(clearOrgano, ORGANOS_QUERY_KEY);
}
