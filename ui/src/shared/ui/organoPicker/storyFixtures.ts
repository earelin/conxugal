import { buildTaxonomiaView, type Organo, type Termo } from '../../lib/taxonomiaTree';

/**
 * The catalogue the picker's three stories draw, kept here rather than in one
 * of them so the tree, the match list and the whole control are all showing the
 * same Órganos.
 *
 * Deliberately not `pickerHarness.tsx`, which holds the same fixtures for the
 * test suite: that module imports `vitest` at its top level, and a story
 * importing it would pull the test runner into the browser bundle.
 */

export const TERMOS: Termo[] = [
  { id: 't-1', name: 'Consellerías', parentId: null },
  { id: 't-2', name: 'Consellería de Educación', parentId: 't-1' },
  { id: 't-3', name: 'Axencias e entidades instrumentais', parentId: 't-2' },
  { id: 't-4', name: 'Consellería de Sanidade', parentId: 't-1' },
  { id: 't-5', name: 'Concellos', parentId: null },
];

export const innovacion: Organo = {
  id: 'o-1',
  name: 'Axencia Galega de Innovación',
  active: true,
  termoId: 't-3',
};

/** Inactive, which is the only state a row marks with a badge. */
export const cunqueiro: Organo = {
  id: 'o-2',
  name: 'Hospital Álvaro Cunqueiro',
  active: false,
  termoId: 't-4',
};

/** Unclassified, so it hangs beside the root terms rather than inside one. */
export const vivenda: Organo = {
  id: 'o-3',
  name: 'Instituto Galego da Vivenda e Solo',
  active: true,
  termoId: null,
};

export const ORGANOS = [innovacion, cunqueiro, vivenda];

export const VIEW = buildTaxonomiaView(TERMOS, ORGANOS);
