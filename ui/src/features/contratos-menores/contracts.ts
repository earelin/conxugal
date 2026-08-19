import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '../../shared/lib/httpClient';

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
  year: number,
): Promise<ContratosMenoresPage> {
  // `year` and nothing else. The ordering and the page are the API's own
  // defaults here, and it refuses a parameter it does not know rather than
  // ignoring it, so sending one it did not ask for is a 400 rather than a
  // harmless extra.
  const query = new URLSearchParams({ year: String(year) });
  const response = await apiFetch(
    `/api/organo/${encodeURIComponent(organoId)}/contratos-menores?${query.toString()}`,
  );
  return response.json() as Promise<ContratosMenoresPage>;
}

/**
 * The one read this slice makes: a page of one Órgano's contratos menores of
 * one year.
 *
 * Keyed on the whole selection rather than on the Órgano alone, so choosing
 * another year is a different query with its own cache entry — not a mutation
 * of the one already answered, which is what would let a stale page sit under a
 * new year while the refetch is in flight.
 */
export function useContratosMenores(organoId: string, year: number) {
  return useQuery({
    queryKey: ['contratos-menores', organoId, year],
    queryFn: () => fetchContratosMenores(organoId, year),
  });
}
