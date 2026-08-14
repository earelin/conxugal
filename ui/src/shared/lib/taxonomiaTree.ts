/**
 * An Órgano as every surface over the taxonomía needs it. The reads that carry
 * more — the administration catalogue and its import mark — declare their own
 * shape extending this one, which is what the type parameter below preserves.
 */
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

/**
 * The type parameter carries a caller's wider Órgano through the tree — the
 * administration catalogue's, which adds the import mark — so one builder
 * serves both reads. Leaving it defaulted is the way a consumer says it reads
 * nothing beyond the four fields above; only the code that needs the wider
 * record names it.
 */
export interface TermoNode<O extends Organo = Organo> {
  id: string;
  name: string;
  children: TermoNode<O>[];
  /** Órganos filed directly in this term, never those of its descendants. */
  organos: O[];
}

export interface TaxonomiaView<O extends Organo = Organo> {
  roots: TermoNode<O>[];
  unclassified: O[];
  /**
   * The whole catalogue as the server sent it. Kept alongside the tree because a
   * picker over every Órgano wants them in name order; reassembling the list
   * from `roots` and `unclassified` would order it by position in the taxonomía
   * instead, and re-sorting is what this module refuses to do.
   */
  catalogue: O[];
}

export const PATH_SEPARATOR = ' › ';

/** A term's place read as one line: `Consellerías › Consellería de Sanidade`. */
export function termoPathLabel(path: TermoNode[]): string {
  return path.map((node) => node.name).join(PATH_SEPARATOR);
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
 *
 * Every term is returned, including the ones holding nothing: a term an
 * administrator has just created has to appear in the management tree the
 * moment it is made. A surface that wants the empty branches gone prunes them
 * itself, with `pruneEmptyTermos`.
 */
export function buildTaxonomiaView<O extends Organo>(
  termos: Termo[],
  organos: O[],
): TaxonomiaView<O> {
  const nodesById = new Map<string, TermoNode<O>>(
    termos.map((termo) => [
      termo.id,
      { id: termo.id, name: termo.name, children: [], organos: [] },
    ]),
  );

  const unclassified: O[] = [];
  for (const organo of organos) {
    const node = organo.termoId === null ? undefined : nodesById.get(organo.termoId);
    if (node) {
      node.organos.push(organo);
    } else {
      unclassified.push(organo);
    }
  }

  const roots: TermoNode<O>[] = [];
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

  return { roots, unclassified, catalogue: organos };
}

/** The chain of terms from a root down to `id`, empty when no term matches. */
export function findTermoPath<O extends Organo>(roots: TermoNode<O>[], id: string): TermoNode<O>[] {
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

/**
 * The tree without the terms holding nothing: a term survives when its own
 * Órgano list is non-empty or any descendant survives the same test. The
 * recursion is the whole point — a single-level check would drop exactly the
 * intermediate terms a deep taxonomía is made of, and with them the branch
 * leading to a term that does hold something.
 *
 * It copies rather than filtering in place: what it is handed is built from a
 * cached read another surface renders whole.
 */
export function pruneEmptyTermos<O extends Organo>(roots: TermoNode<O>[]): TermoNode<O>[] {
  return roots.flatMap((node) => {
    const children = pruneEmptyTermos(node.children);
    return node.organos.length > 0 || children.length > 0 ? [{ ...node, children }] : [];
  });
}
