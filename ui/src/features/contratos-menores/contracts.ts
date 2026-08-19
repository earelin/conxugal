import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '../../shared/lib/httpClient';
import type { Selection, Sort } from './selection';

/**
 * The whole selection, which is what the cache is keyed on: no two selections
 * share an entry, and the key is what says whether an answer already on screen
 * belongs to the one now being asked for.
 */
type ContratosMenoresKey = ['contratos-menores', string, number, Sort, number];

/**
 * Who was awarded the contract: the operador's selected name and its canonical
 * fiscal identifier, and no identity of its own — nothing has an operador route
 * to cross into yet.
 */
export interface Awardee {
  name: string;
  fiscalId: string;
}

/**
 * One contrato menor as this list shows it, which is everything the system
 * holds: the family has no detail view to click into.
 *
 * The date, the amount and the awardee are **never null** — a contract missing
 * any of them is withheld from every read rather than shown incomplete — so no
 * absent-value rendering exists for them here. `obxecto` and `duration` are the
 * only two that can be absent, the source publishing neither for some awards,
 * and they arrive as explicit nulls rather than as missing keys.
 */
export interface ContratoMenor {
  /**
   * The source's own identifier, and the contract's identity here too. Declared
   * `int64` on the wire and read as a JS number, which rounds past 2^53 — a
   * published identifier is seven digits, so the two only part company on a
   * value the source has never issued.
   */
  sourceId: number;
  /** An ISO calendar date, `YYYY-MM-DD`, with no time and no zone. */
  publicationDate: string;
  obxecto: string | null;
  /** Euros, VAT included, exactly as published — unbounded on purpose. */
  amount: number;
  duration: string | null;
  awardee: Awardee;
  sourceUrl: string;
}

/** The paging envelope every paginated read in this API answers with. */
export interface ContratosMenoresPage {
  items: ContratoMenor[];
  /** 1-based, and the page that was asked for: nothing is clamped. */
  page: number;
  size: number;
  /** The whole selection, not this page, and the same on every page of it. */
  totalItems: number;
  totalPages: number;
}

async function fetchContratosMenores(
  organoId: string,
  { year, sort, page }: Selection,
): Promise<ContratosMenoresPage> {
  // The three the URL carries, and nothing else: `size` is the API's own
  // default, since the reader never chooses one and no control offers it. The
  // API refuses a parameter it does not know rather than ignoring it, so
  // sending one it did not ask for is a 400 rather than a harmless extra.
  //
  // The ordering and the page are stated rather than left to the same defaults,
  // because the request is then the whole selection — the response states
  // neither, so a request that named only what differs from the default would
  // leave nothing anywhere saying what was asked for.
  const query = new URLSearchParams({ year: String(year), sort, page: String(page) });
  const response = await apiFetch(
    `/api/organo/${encodeURIComponent(organoId)}/contratos-menores?${query.toString()}`,
  );
  return response.json() as Promise<ContratosMenoresPage>;
}

/**
 * The one read this slice makes: a page of one Órgano's contratos menores of
 * one year, in one ordering.
 *
 * Keyed on the whole selection rather than on the Órgano alone, so changing any
 * part of it is a different query with its own cache entry — not a mutation of
 * the one already answered.
 *
 * The page already answered is held while the **next page of the same year and
 * ordering** is fetched. Without it a new key has no data, the list unmounts to
 * a spinner, and the paging control goes with it — taking the stated count and
 * page total off screen and dropping keyboard focus from the button just
 * pressed. Moving between pages changes neither of those numbers, so neither may
 * disappear while it happens: only the window over the selection moves.
 *
 * **Held only within one selection**, which is why this is not `keepPreviousData`:
 * that is `(previous) => previous` and compares no keys, so it would hold an
 * answer across a change of year or ordering too — and there the count, the page
 * total and the page in force *do* change, so the control would state the old
 * selection's numbers with nothing saying they were stale. A change of selection
 * is a wait, not a window moving.
 */
export function useContratosMenores(organoId: string, selection: Selection) {
  const { year, sort, page } = selection;
  return useQuery({
    queryKey: ['contratos-menores', organoId, year, sort, page],
    queryFn: () => fetchContratosMenores(organoId, selection),
    placeholderData: (previous, previousQuery) => {
      const [, heldOrgano, heldYear, heldSort] = (previousQuery?.queryKey ??
        []) as Partial<ContratosMenoresKey>;
      const sameSelection = heldOrgano === organoId && heldYear === year && heldSort === sort;
      return sameSelection ? previous : undefined;
    },
  });
}
