import { describe, expect, it } from 'vitest';

import type { Organo, TermoNode } from '../../lib/taxonomiaTree';
import { isTermo, organoIdOf, toTreeData, treeValues } from './treeData';

function organo(id: string, name: string): Organo {
  return { id, name, active: true, termoId: null };
}

function node(id: string, name: string, organos: Organo[], children: TermoNode[] = []): TermoNode {
  return { id, name, children, organos };
}

const sergas = organo('o-1', 'Servizo Galego de Saúde');
const cunqueiro = organo('o-2', 'Hospital Álvaro Cunqueiro');
const vivenda = organo('o-3', 'Instituto Galego da Vivenda e Solo');

describe('organoIdOf', () => {
  it('reads the Organo a row stands for', () => {
    expect(organoIdOf('organo:o-1')).toBe('o-1');
  });

  it('answers null for a term row, which stands for no Organo', () => {
    expect(organoIdOf('termo:t-1')).toBeNull();
  });
});

describe('isTermo', () => {
  it('tells the two kinds of row apart by what its value is prefixed with', () => {
    expect(isTermo('termo:t-1')).toBe(true);
    expect(isTermo('organo:o-1')).toBe(false);
  });
});

describe('toTreeData', () => {
  it('puts the child terms before the terms own Organos', () => {
    const data = toTreeData(
      [node('t-1', 'Consellerías', [sergas], [node('t-2', 'Sanidade', [])])],
      [],
    );

    expect(data[0].children?.map((child) => child.label)).toEqual([
      'Sanidade',
      'Servizo Galego de Saúde',
    ]);
  });

  it('puts the unclassified Organos at the root, after the root terms', () => {
    const data = toTreeData([node('t-1', 'Consellerías', [])], [vivenda]);

    expect(data.map((item) => item.label)).toEqual([
      'Consellerías',
      'Instituto Galego da Vivenda e Solo',
    ]);
  });

  it('namespaces a row by the table its id came from', () => {
    const data = toTreeData([node('t-1', 'Consellerías', [sergas])], [vivenda]);

    expect(data[0].value).toBe('termo:t-1');
    expect(data[0].children?.[0].value).toBe('organo:o-1');
    expect(data[1].value).toBe('organo:o-3');
  });

  it('preserves the order it is given rather than sorting, at every level', () => {
    // Both reads arrive name-ordered under a collation the server owns, so a
    // sort here would be a second answer to what that order is.
    const data = toTreeData([node('t-1', 'Consellerías', [cunqueiro, sergas])], []);

    expect(data[0].children?.map((child) => child.label)).toEqual([
      'Hospital Álvaro Cunqueiro',
      'Servizo Galego de Saúde',
    ]);
  });
});

describe('treeValues', () => {
  it('lists every value in the tree, however deep', () => {
    const data = toTreeData(
      [node('t-1', 'Consellerías', [], [node('t-2', 'Sanidade', [sergas])])],
      [vivenda],
    );

    expect(treeValues(data)).toEqual(['termo:t-1', 'termo:t-2', 'organo:o-1', 'organo:o-3']);
  });

  it('answers nothing for an empty tree, which is what an empty catalogue draws', () => {
    expect(treeValues([])).toEqual([]);
  });
});
