import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { apiFetch } from '../../shared/lib/httpClient';
import { buildTaxonomiaView, type TaxonomiaView } from './taxonomiaTree';

export interface Organo {
  id: string;
  name: string;
  active: boolean;
  /** The term this Órgano is filed under; null means unclassified. */
  termoId: string | null;
}

export interface Termo {
  id: string;
  name: string;
  /** The term this one sits under; null marks a root. */
  parentId: string | null;
}

// Both keys share the ['organos'] prefix so a mutation can invalidate the whole
// section with one call, or either read on its own.
export const ORGANOS_QUERY_KEY = ['organos', 'catalogo'] as const;
export const TAXONOMIA_QUERY_KEY = ['organos', 'taxonomia'] as const;

async function fetchOrganos(): Promise<Organo[]> {
  const response = await apiFetch('/api/organos');
  return response.json() as Promise<Organo[]>;
}

async function fetchTaxonomia(): Promise<Termo[]> {
  const response = await apiFetch('/api/organos/taxonomia');
  return response.json() as Promise<Termo[]>;
}

export interface OrganosTaxonomia {
  /** The joined view; null unless *both* reads are currently succeeding. */
  view: TaxonomiaView | null;
  isPending: boolean;
  /** Whether either read is in flight, including a retry of a failed one. */
  isFetching: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => void;
}

/**
 * The section's single read. Both lists are fetched and joined here, and every
 * caller that mutates the taxonomía or a placement refetches through it.
 *
 * A failing read nulls `view` rather than letting the join run on half the data.
 * That is the whole point: a failed taxonomy fetch reaching the builder as an
 * empty term list would place the entire catalogue under "sen clasificar", and
 * an admin would read a transport failure as their taxonomy having been wiped.
 * The rule lives here rather than in the components so a caller cannot render
 * the successful half by forgetting to check `isError` — note that a *refetch*
 * failure leaves react-query's own `data` populated with the last good
 * response, so guarding on `data` alone would not be enough.
 */
export function useOrganosTaxonomia(): OrganosTaxonomia {
  const organos = useQuery({ queryKey: ORGANOS_QUERY_KEY, queryFn: fetchOrganos });
  const taxonomia = useQuery({ queryKey: TAXONOMIA_QUERY_KEY, queryFn: fetchTaxonomia });

  const isError = organos.isError || taxonomia.isError;

  const view = useMemo(
    () =>
      organos.data && taxonomia.data && !isError
        ? buildTaxonomiaView(taxonomia.data, organos.data)
        : null,
    [organos.data, taxonomia.data, isError],
  );

  return {
    view,
    isPending: organos.isPending || taxonomia.isPending,
    isFetching: organos.isFetching || taxonomia.isFetching,
    isError,
    error: organos.error ?? taxonomia.error,
    refetch: () => {
      void organos.refetch();
      void taxonomia.refetch();
    },
  };
}
