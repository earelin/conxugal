import type { TreeNodeData } from '@mantine/core';

import type { Organo, TermoNode } from '../../lib/taxonomiaTree';

// Term ids and Órgano ids come from different tables, so a row's value says
// which of the two it is: a term row only opens its branch, an Órgano row
// opens its contracts. Every reader of that distinction goes through the two
// functions below rather than slicing the prefix itself.
const TERMO = 'termo:';
export const ORGANO = 'organo:';

export function isTermo(value: string): boolean {
  return value.startsWith(TERMO);
}

/** The Órgano a row stands for, or null when the row is a term. */
export function organoIdOf(value: string): string | null {
  return value.startsWith(ORGANO) ? value.slice(ORGANO.length) : null;
}

function organoNodes(organos: Organo[]): TreeNodeData[] {
  return organos.map((organo) => ({ value: `${ORGANO}${organo.id}`, label: organo.name }));
}

/**
 * Child terms first, then the term's own Órganos — at the root too, which is
 * what puts the unclassified Órganos beside the root terms rather than under a
 * heading of their own. Nothing sorts: both reads arrive in name order.
 */
export function toTreeData(nodes: TermoNode[], organos: Organo[]): TreeNodeData[] {
  const termos = nodes.map((node) => ({
    value: `${TERMO}${node.id}`,
    label: node.name,
    children: toTreeData(node.children, node.organos),
  }));
  return [...termos, ...organoNodes(organos)];
}

/** Every value in the tree, which is what changes when the taxonomía does. */
export function treeValues(nodes: TreeNodeData[]): string[] {
  return nodes.flatMap((node) => [node.value, ...treeValues(node.children ?? [])]);
}
