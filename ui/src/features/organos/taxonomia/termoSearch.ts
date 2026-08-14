import { foldForSearch } from '../../../shared/lib/organoSearch';
import { findTermoPath, type TermoNode } from '../../../shared/lib/taxonomiaTree';

export interface TermoPickerRow {
  id: string;
  name: string;
  /** Depth in the taxonomía, which is the row's indent — not its index. */
  depth: number;
}

/**
 * The rows the assign picker draws: the taxonomía filtered by `query`, flattened
 * in tree order with each row's depth.
 *
 * A term is kept when it matches, when one of its ancestors matches (a match is
 * usually a branch head, and a picker that showed «Consellería de Sanidade» with
 * no way to reach the terms under it would be worse than no search), when one of
 * its descendants matches (otherwise the indentation of the survivors reads as
 * nonsense), or when it is the chosen term or an ancestor of it — the current
 * answer is never hidden by the question, or an administrator could narrow the
 * search until their own choice left the panel and then confirm it blind.
 *
 * An empty query matches everything, so the unfiltered tree is not a special
 * case. Matches are not highlighted: `deburr` changes a string's length, so a
 * folded offset does not map back onto the original, and marking the span would
 * need a per-character folder of our own.
 */
export function buildTermoPickerRows(
  roots: TermoNode[],
  query: string,
  selectedId: string | null,
): TermoPickerRow[] {
  const folded = foldForSearch(query.trim());
  const pinned = new Set(
    selectedId === null ? [] : findTermoPath(roots, selectedId).map((node) => node.id),
  );
  return flatten(roots, retained(roots, folded, pinned), 0);
}

/**
 * The ids to draw, in one post-order walk: a subtree is retained when it holds a
 * match or a pinned term anywhere, and every term below a match is retained with
 * it.
 */
function retained(
  nodes: TermoNode[],
  folded: string,
  pinned: ReadonlySet<string>,
  underMatch = false,
): Set<string> {
  const keep = new Set<string>();
  for (const node of nodes) {
    const matches = underMatch || foldForSearch(node.name).includes(folded);
    const below = retained(node.children, folded, pinned, matches);
    if (matches || below.size > 0 || pinned.has(node.id)) {
      keep.add(node.id);
      for (const id of below) {
        keep.add(id);
      }
    }
  }
  return keep;
}

function flatten(nodes: TermoNode[], keep: ReadonlySet<string>, depth: number): TermoPickerRow[] {
  return nodes.flatMap((node) =>
    keep.has(node.id)
      ? [{ id: node.id, name: node.name, depth }, ...flatten(node.children, keep, depth + 1)]
      : [],
  );
}
