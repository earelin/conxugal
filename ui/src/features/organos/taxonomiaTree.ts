import type { Organo, Termo } from './organos';

export interface TermoNode {
  id: string;
  name: string;
  children: TermoNode[];
  /** Órganos filed directly in this term, never those of its descendants. */
  organos: Organo[];
}

export interface TaxonomiaView {
  roots: TermoNode[];
  unclassified: Organo[];
}

/**
 * Joins the two flat reads into the rooted taxonomía plus the unclassified
 * worklist. The single place tree shape is computed, and pure so it can be
 * exercised without rendering.
 *
 * It never sorts. Both reads arrive in name order under a Galician collation
 * the server owns, and grouping preserves relative order, so siblings stay in
 * name order for free. Re-sorting here would be a second source of truth that
 * disagreed with the server on exactly the accented names this catalogue is
 * full of.
 *
 * The two reads are separate requests, so another admin's delete can land
 * between them and leave an edge pointing at a term that is no longer in the
 * taxonomía. Such an edge is dropped, never the record it came from: an Órgano
 * surfaces as unclassified and a term as a root.
 */
export function buildTaxonomiaView(termos: Termo[], organos: Organo[]): TaxonomiaView {
  const nodesById = new Map<string, TermoNode>(
    termos.map((termo) => [
      termo.id,
      { id: termo.id, name: termo.name, children: [], organos: [] },
    ]),
  );

  const unclassified: Organo[] = [];
  for (const organo of organos) {
    const node = organo.termoId === null ? undefined : nodesById.get(organo.termoId);
    if (node) {
      node.organos.push(organo);
    } else {
      unclassified.push(organo);
    }
  }

  const roots: TermoNode[] = [];
  for (const termo of termos) {
    const node = nodesById.get(termo.id);
    if (!node) {
      continue;
    }
    const parent = termo.parentId === null ? undefined : nodesById.get(termo.parentId);
    if (parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }

  return { roots, unclassified };
}

/** The chain of terms from a root down to `id`, empty when no term matches. */
export function findTermoPath(roots: TermoNode[], id: string): TermoNode[] {
  for (const root of roots) {
    if (root.id === id) {
      return [root];
    }
    const path = findTermoPath(root.children, id);
    if (path.length > 0) {
      return [root, ...path];
    }
  }
  return [];
}
