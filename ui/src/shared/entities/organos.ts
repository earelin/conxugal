import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { apiFetch } from '../lib/httpClient';
import {
  buildTaxonomiaView,
  type Organo,
  type TaxonomiaView,
  type Termo,
} from '../lib/taxonomiaTree';

interface ReadOptions {
  /**
   * Off until the caller knows there is a session to read for. A disabled query
   * reports `isPending` for as long as it stays off, so a caller that gates the
   * read must gate the rendering on the same condition.
   */
  enabled?: boolean;
}

// Every key shares the ['organos'] prefix, so one invalidation can refresh
// whatever a write touched: the whole area, or a single read.
export const ORGANOS_KEY_PREFIX = ['organos'] as const;
export const TAXONOMIA_QUERY_KEY = [...ORGANOS_KEY_PREFIX, 'taxonomia'] as const;
export const VISIBLE_ORGANOS_QUERY_KEY = [...ORGANOS_KEY_PREFIX, 'visibles'] as const;

async function fetchTaxonomia(): Promise<Termo[]> {
  const response = await apiFetch('/api/organos/taxonomia');
  return response.json() as Promise<Termo[]>;
}

/**
 * The Órganos a reader may browse — those holding at least one visible
 * contract — rather than the whole catalogue the administration area files.
 */
async function fetchVisibleOrganos(): Promise<Organo[]> {
  const response = await apiFetch('/api/organos');
  return response.json() as Promise<Organo[]>;
}

/**
 * The taxonomía, read from here rather than from either surface that draws it:
 * one fetcher and one key mean the browse picker and the administration section
 * share an in-flight request and a cache entry, so an administrator's edit
 * refreshes both.
 */
export function useTaxonomia({ enabled = true }: ReadOptions = {}) {
  return useQuery({ queryKey: TAXONOMIA_QUERY_KEY, queryFn: fetchTaxonomia, enabled });
}

export interface VisibleOrganos {
  /** The joined view; null unless *both* reads are currently succeeding. */
  view: TaxonomiaView | null;
  isPending: boolean;
  /** Whether either read is in flight, including a retry of a failed one. */
  isFetching: boolean;
  isError: boolean;
  refetch: () => void;
}

/**
 * The browse read: the visible set joined to the taxonomía it is classified
 * into.
 *
 * A failing read nulls `view` rather than letting the join run on half the
 * data. A failed taxonomy fetch reaching the builder as an empty term list
 * would render the whole visible set as one flat unclassified heap, which reads
 * as an answer rather than as the failure it is. Guarding on `data` alone would
 * not be enough — a *refetch* failure leaves react-query's own `data` holding
 * the last good response.
 */
export function useVisibleOrganos({ enabled = true }: ReadOptions = {}): VisibleOrganos {
  const organos = useQuery({
    queryKey: VISIBLE_ORGANOS_QUERY_KEY,
    queryFn: fetchVisibleOrganos,
    enabled,
  });
  const taxonomia = useTaxonomia({ enabled });

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
    refetch: () => {
      void organos.refetch();
      void taxonomia.refetch();
    },
  };
}
