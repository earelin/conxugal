import { describe, expect, it } from 'vitest';

import { buildTaxonomiaView } from '../../../shared/lib/taxonomiaTree';
import type { Organo, Termo } from '../organos';
import { buildTermoPickerRows } from './termoSearch';

function termo(id: string, name: string, parentId: string | null = null): Termo {
  return { id, name, parentId };
}

const TAXONOMIA = [
  termo('t-1', 'Consellerías'),
  termo('t-2', 'Consellería de Sanidade', 't-1'),
  termo('t-3', 'Consellería de Educación', 't-1'),
  termo('t-4', 'Axencia Galega de Innovación', 't-3'),
  termo('t-5', 'Concellos'),
];

const NO_ORGANOS: Organo[] = [];

const { roots } = buildTaxonomiaView(TAXONOMIA, NO_ORGANOS);

function names(query: string, selectedId: string | null = null) {
  return buildTermoPickerRows(roots, query, selectedId).map((row) => row.name);
}

describe('buildTermoPickerRows', () => {
  it('returns the whole taxonomía in tree order when nothing is typed', () => {
    expect(names('')).toEqual([
      'Consellerías',
      'Consellería de Sanidade',
      'Consellería de Educación',
      'Axencia Galega de Innovación',
      'Concellos',
    ]);
  });

  it('reports each row its depth, which is what the picker indents by', () => {
    const rows = buildTermoPickerRows(roots, '', null);

    expect(rows.map((row) => row.depth)).toEqual([0, 1, 1, 2, 0]);
  });

  it('matches without accents, so «educacion» finds «Educación»', () => {
    expect(names('educacion')).toContain('Consellería de Educación');
  });

  it('keeps the ancestors of a match, so the indentation still reads', () => {
    expect(names('sanidade')).toEqual(['Consellerías', 'Consellería de Sanidade']);
  });

  it('keeps everything under a match, since a match is usually a branch head', () => {
    expect(names('educacion')).toEqual([
      'Consellerías',
      'Consellería de Educación',
      'Axencia Galega de Innovación',
    ]);
  });

  // The match is two levels down, so keeping it needs the whole chain above it
  // — the case that proves the kept set is closed under parents rather than
  // merely under immediate ones.
  it('keeps every ancestor of a match, not just its parent', () => {
    expect(names('innovacion')).toEqual([
      'Consellerías',
      'Consellería de Educación',
      'Axencia Galega de Innovación',
    ]);
  });

  it('keeps the chosen term visible however narrow the query gets', () => {
    expect(names('concellos', 't-2')).toEqual([
      'Consellerías',
      'Consellería de Sanidade',
      'Concellos',
    ]);
  });

  it('returns nothing when only the query is left unmatched', () => {
    expect(names('zzz')).toEqual([]);
  });

  it('ignores the whitespace around a query', () => {
    expect(names('  sanidade  ')).toEqual(names('sanidade'));
  });
});
