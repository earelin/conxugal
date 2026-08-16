import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '../lib/httpClient';

/**
 * One Órgano as its own page reads it, which is a different serialisation from
 * the catalogue row in `taxonomiaTree`: no `active`, no `termoId`, because the
 * page renders neither.
 */
export interface OrganoMember {
  id: string;
  name: string;
  /**
   * One entry per contract family the Órgano holds visible data for. Values are
   * opaque here on purpose: a shared module that knew what a family's summary
   * contains would be the shared core depending on a feature, which is the one
   * direction the layering forbids. Each family's slice narrows its own entry.
   */
  families: Record<string, unknown>;
}

/**
 * What the `/organo/:id` layout route hands the family section mounted in its
 * outlet. It lives here rather than in either slice because both ends need it
 * and neither may import the other.
 */
export interface OrganoOutletContext {
  organo: OrganoMember;
  /** The active family's entry, narrowed by the section that understands it. */
  family: unknown;
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
