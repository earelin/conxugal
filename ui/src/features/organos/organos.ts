import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';

import { ORGANOS_KEY_PREFIX, useTaxonomia } from '../../shared/entities/organos';
import { apiFetch } from '../../shared/lib/httpClient';
import {
  buildTaxonomiaView,
  type Organo as CatalogueOrgano,
  type TaxonomiaView,
} from '../../shared/lib/taxonomiaTree';

export type { Termo } from '../../shared/lib/taxonomiaTree';

/**
 * How far an Órgano's contratos menores history has been loaded. Three values
 * rather than two: a never-loaded Órgano and a half-loaded one both fall short
 * of complete, and collapsing them would let an interrupted import read as up
 * to date.
 */
export type ContratosMenoresImportState = 'NEVER_STARTED' | 'INCOMPLETE' | 'COMPLETE';

/**
 * An Órgano as an administrator sees it: everything the browse catalogue
 * carries, plus the contratos menores mark and how far that import got. The
 * shared read serves neither, which is why this is a wider shape over the same
 * records rather than a second declaration of them.
 */
export interface Organo extends CatalogueOrgano {
  /** Whether an administrator opted this Órgano into contratos menores import. */
  importable: boolean;
  /**
   * How far that import got, independent of the mark: unmarking keeps whatever
   * state was reached, because the contracts already stored are kept.
   */
  importState: ContratosMenoresImportState;
}

// The prefix every Órganos read shares, so a mutation can invalidate the whole
// section with one call, or a single read on its own.
export const SECTION_QUERY_KEY = ORGANOS_KEY_PREFIX;
export const ORGANOS_QUERY_KEY = [...ORGANOS_KEY_PREFIX, 'catalogo'] as const;

/**
 * The `ADMIN`-gated catalogue, not the shared `/api/organos`: this section files the
 * whole of it — the unclassified, the inactive and the ones holding no contract at
 * all — while the shared read is scoped to what a reader may browse. The two answer
 * different questions, so the section asks the one that means what it wants rather
 * than filtering here. It is also the only read carrying the import mark and the
 * import state, which the contratos menores column renders.
 */
async function fetchOrganos(): Promise<Organo[]> {
  const response = await apiFetch('/api/admin/organos');
  return response.json() as Promise<Organo[]>;
}

export interface OrganosTaxonomia {
  /** The joined view; null unless *both* reads are currently succeeding. */
  view: TaxonomiaView<Organo> | null;
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
  // The shared taxonomía read: the browse picker asks for the same list, and
  // sharing the key is what makes the section's writes refresh it too.
  const taxonomia = useTaxonomia();

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

/**
 * Every write in the section invalidates rather than patching the cache: both
 * reads are flat lists the client re-assembles, so a refetch plus the existing
 * builder is the whole update — there is no server-assembled shape to keep in
 * sync, and no write a local edit could describe more cheaply than re-reading
 * one small list.
 *
 * It lives here, beside the keys, because the taxonomía and the placements are
 * written by two modules against the same two reads.
 */
export function useOrganosMutation<TInput, TResult>(
  mutationFn: (input: TInput) => Promise<TResult>,
  queryKey: readonly unknown[],
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey }),
  });
}
