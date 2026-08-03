import { useMutation, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '../../shared/lib/httpClient';
import { SECTION_QUERY_KEY, TAXONOMIA_QUERY_KEY, type Termo } from './organos';

const TERMOS_PATH = '/api/admin/organos/taxonomia/termos';
const TERMO_PATH = '/api/admin/organos/taxonomia/termo';

function jsonBody(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

export interface CreateTermoInput {
  name: string;
  /** The term to create this one under; null creates it at the root. */
  parentId: string | null;
}

async function createTermo(input: CreateTermoInput): Promise<Termo> {
  const response = await apiFetch(TERMOS_PATH, jsonBody('POST', input));
  return response.json() as Promise<Termo>;
}

export interface RenameTermoInput {
  id: string;
  name: string;
}

async function renameTermo({ id, name }: RenameTermoInput): Promise<Termo> {
  const response = await apiFetch(`${TERMO_PATH}/${id}`, jsonBody('PATCH', { name }));
  return response.json() as Promise<Termo>;
}

export interface MoveTermoInput {
  id: string;
  /**
   * The term to move under. Null is a destination — the root — not an omission,
   * which is why the field is always sent.
   */
  parentId: string | null;
}

async function moveTermo({ id, parentId }: MoveTermoInput): Promise<void> {
  await apiFetch(`${TERMO_PATH}/${id}/parent`, jsonBody('PUT', { parentId }));
}

async function deleteTermo(id: string): Promise<void> {
  await apiFetch(`${TERMO_PATH}/${id}`, { method: 'DELETE' });
}

/**
 * Every write invalidates rather than patching the cache: the taxonomía read is
 * a flat list the client re-assembles into a tree, so a refetch plus the
 * existing builder is the whole update — there is no server-assembled shape to
 * keep in sync, and no move or delete that a local edit could describe more
 * cheaply than re-reading one small list.
 */
function useTaxonomiaMutation<TInput, TResult>(
  mutationFn: (input: TInput) => Promise<TResult>,
  queryKey: readonly unknown[],
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey }),
  });
}

export function useCreateTermo() {
  return useTaxonomiaMutation(createTermo, TAXONOMIA_QUERY_KEY);
}

export function useRenameTermo() {
  return useTaxonomiaMutation(renameTermo, TAXONOMIA_QUERY_KEY);
}

export function useMoveTermo() {
  return useTaxonomiaMutation(moveTermo, TAXONOMIA_QUERY_KEY);
}

export function useDeleteTermo() {
  // The prefix both reads share: a delete returns the term's Órganos to the
  // unclassified worklist, so the catalogue is as stale as the taxonomía.
  return useTaxonomiaMutation(deleteTermo, SECTION_QUERY_KEY);
}
