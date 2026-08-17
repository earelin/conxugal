import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '../lib/httpClient';

/**
 * What every family entry carries, whichever family it is: the envelope this
 * read publishes, and nothing of what is inside it.
 */
export interface OrganoFamily {
  /** The path segment this family's section is mounted at, as the server states it. */
  route: string;
  /**
   * Opaque here on purpose: a shared module that knew what a family's summary
   * contains would be the shared core depending on a feature, which is the one
   * direction the layering forbids. Only that family's slice may narrow it.
   */
  summary: unknown;
}

/**
 * One Órgano as its own page reads it, which is a different serialisation from
 * the catalogue row in `taxonomiaTree`: no `active`, no `termoId`, because the
 * page renders neither.
 */
export interface OrganoMember {
  id: string;
  name: string;
  /** One entry per contract family the Órgano holds visible data for. */
  families: Record<string, OrganoFamily>;
}

/**
 * What the `/organo/:id` layout route hands the family section mounted in its
 * outlet. It lives here rather than in either slice because both ends need it
 * and neither may import the other.
 *
 * The section takes its own summary out of `family.summary` and narrows it
 * there — typed rather than left `unknown` whole, so reaching for a field of the
 * summary on the entry itself is a compiler error and not a silent `undefined`.
 */
export interface OrganoOutletContext {
  organo: OrganoMember;
  family: OrganoFamily;
}

async function fetchOrgano(id: string): Promise<OrganoMember> {
  const response = await apiFetch(`/api/organo/${encodeURIComponent(id)}`);
  return response.json() as Promise<OrganoMember>;
}

/**
 * The one read the Órgano page makes: the name, the families it holds and each
 * family's summary, in a single request. A failure carries its status, so a 404
 * — an id the catalogue does not know — stays distinguishable from a read that
 * could not be made at all.
 */
export function useOrgano(id: string) {
  return useQuery({ queryKey: ['organo', id], queryFn: () => fetchOrgano(id) });
}
