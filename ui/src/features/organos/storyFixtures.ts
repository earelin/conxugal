import { buildTaxonomiaView, findTermoPath, type TermoNode } from '../../shared/lib/taxonomiaTree';
import type { ContratosMenoresImportState, Organo, Termo } from './organos';

/**
 * The administration catalogue the organos stories draw. One taxonomía and one
 * set of Órganos, shared by the tables, the tree and every dialog, so the whole
 * feature's stories are showing the same section.
 *
 * The Órganos are chosen to cover all six states `markState` can return —
 * `ineligible`, `none`, `marked`, `partial`, `imported`, `stale` — since that
 * is what the mark cell and the catalogue table are read for.
 */

export const TERMOS: Termo[] = [
  { id: 't-1', name: 'Consellerías', parentId: null },
  { id: 't-2', name: 'Consellería de Sanidade', parentId: 't-1' },
  { id: 't-3', name: 'Consellería de Educación', parentId: 't-1' },
  { id: 't-4', name: 'Axencias e entidades instrumentais', parentId: 't-3' },
  { id: 't-5', name: 'Concellos', parentId: null },
];

function organo(
  id: string,
  name: string,
  termoId: string | null,
  {
    active = true,
    importable = false,
    importState = 'NEVER_STARTED',
  }: { active?: boolean; importable?: boolean; importState?: ContratosMenoresImportState } = {},
): Organo {
  return { id, name, active, termoId, importable, importState };
}

/** Marked, nothing loaded yet. */
export const sergas = organo('o-1', 'Servizo Galego de Saúde', 't-2', {
  importable: true,
});

/** Marked, an import that got part of the way. */
export const cunqueiro = organo('o-2', 'Hospital Álvaro Cunqueiro', 't-2', {
  importable: true,
  importState: 'INCOMPLETE',
});

/** Marked and fully loaded. */
export const innovacion = organo('o-3', 'Axencia Galega de Innovación', 't-4', {
  importable: true,
  importState: 'COMPLETE',
});

/** Unmarked but holding history, which is the state that earns its own badge. */
export const universidade = organo('o-4', 'Universidade de Santiago de Compostela', 't-3', {
  importState: 'COMPLETE',
});

/** Inactive, so its switch is blocked and says why. */
export const portos = organo('o-5', 'Portos de Galicia', 't-4', { active: false });

/** Unclassified, and nothing stored: the plainest row there is. */
export const vivenda = organo('o-6', 'Instituto Galego da Vivenda e Solo', null);

export const ORGANOS = [sergas, cunqueiro, innovacion, universidade, portos, vivenda];

export const VIEW = buildTaxonomiaView(TERMOS, ORGANOS);

/** The Órganos filed under Consellería de Sanidade, as a term's table holds them. */
export const SANIDADE_ORGANOS = [sergas, cunqueiro];

/**
 * A term of the built tree by id. Throws rather than returning undefined so a
 * fixture id that stops matching fails the story build instead of quietly
 * handing every dialog an empty term.
 */
function node(id: string): TermoNode<Organo> {
  const found = findTermoPath(VIEW.roots, id).at(-1);
  if (found === undefined) {
    throw new Error(`No termo fixture with id ${id}`);
  }
  return found;
}

/**
 * No child terms, so a delete is allowed. The two Órganos it holds do not block
 * one — they return to the worklist rather than being deleted with it.
 */
export const SANIDADE = node('t-2');

/** Holds a child term, which is what blocks a delete before it is even sent. */
export const EDUCACION = node('t-3');

export const CONSELLERIAS = node('t-1');
