import { strings } from '../../shared/lib/strings';
import type { Organo } from './organos';
import { findTermoPath, type TermoNode, termoPathLabel } from './taxonomiaTree';

/**
 * Where an Órgano sits now, read as one line. Lives beside `taxonomiaTree.ts`
 * rather than in it because the unclassified case is a piece of Galician copy,
 * and the tree module is deliberately string-free.
 *
 * A `termoId` pointing at a term this browser has not read resolves to the same
 * "Sen clasificar" the builder already files it under, so the dialog and the
 * worklist cannot disagree.
 */
export function organoPlacementLabel(roots: TermoNode[], organo: Organo): string {
  const path = organo.termoId === null ? [] : findTermoPath(roots, organo.termoId);
  return path.length > 0 ? termoPathLabel(path) : strings.admin.organos.unclassified;
}
