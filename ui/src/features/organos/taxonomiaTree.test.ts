import { describe, expect, it } from 'vitest';

import type { Organo, Termo } from './organos';
import { buildTaxonomiaView, findTermoPath } from './taxonomiaTree';

function termo(id: string, name: string, parentId: string | null = null): Termo {
  return { id, name, parentId };
}

function organo(id: string, name: string, termoId: string | null, active = true): Organo {
  return { id, name, active, termoId };
}

const consellerias = termo('t-1', 'Consellerías');
const sanidade = termo('t-2', 'Consellería de Sanidade', 't-1');
const educacion = termo('t-3', 'Consellería de Educación', 't-1');
const innovacion = termo('t-4', 'Axencia Galega de Innovación', 't-3');
const concellos = termo('t-5', 'Concellos');

const sergas = organo('o-1', 'Servizo Galego de Saúde', 't-2');
const urxencias = organo('o-2', 'Fundación Pública Urxencias Sanitarias', 't-2');
const cunqueiro = organo('o-3', 'Hospital Álvaro Cunqueiro', 't-2', false);
const vivenda = organo('o-4', 'Instituto Galego da Vivenda e Solo', null);

describe('buildTaxonomiaView', () => {
  it('nests terms several levels deep under their parents', () => {
    const { roots } = buildTaxonomiaView(
      [consellerias, sanidade, educacion, innovacion, concellos],
      [],
    );

    expect(roots.map((node) => node.name)).toEqual(['Consellerías', 'Concellos']);
    expect(roots[0].children.map((node) => node.name)).toEqual([
      'Consellería de Sanidade',
      'Consellería de Educación',
    ]);
    expect(roots[0].children[1].children.map((node) => node.name)).toEqual([
      'Axencia Galega de Innovación',
    ]);
  });

  it('files each Organo under the term its termoId names', () => {
    const { roots } = buildTaxonomiaView(
      [consellerias, sanidade, educacion],
      [sergas, urxencias, cunqueiro],
    );

    expect(roots[0].children[0].organos).toEqual([sergas, urxencias, cunqueiro]);
    expect(roots[0].children[1].organos).toEqual([]);
    expect(roots[0].organos).toEqual([]);
  });

  it('returns every null-placement Organo as unclassified', () => {
    const { unclassified } = buildTaxonomiaView([consellerias, sanidade], [sergas, vivenda]);

    expect(unclassified).toEqual([vivenda]);
  });

  it('returns the whole catalogue as unclassified when the taxonomia is empty', () => {
    const { roots, unclassified } = buildTaxonomiaView([], [sergas, urxencias, vivenda]);

    expect(roots).toEqual([]);
    expect(unclassified).toEqual([sergas, urxencias, vivenda]);
  });

  it('shows an Organo whose termoId matches no term as unclassified rather than losing it', () => {
    const orphan = organo('o-9', 'Axencia de Turismo de Galicia', 't-deleted');

    const { roots, unclassified } = buildTaxonomiaView([consellerias, sanidade], [sergas, orphan]);

    expect(unclassified).toEqual([orphan]);
    expect(roots[0].children[0].organos).toEqual([sergas]);
  });

  it('shows a term whose parentId matches no term as a root rather than losing it', () => {
    const orphan = termo('t-9', 'Deputacións provinciais', 't-deleted');

    const { roots } = buildTaxonomiaView([consellerias, orphan], []);

    expect(roots.map((node) => node.name)).toEqual(['Consellerías', 'Deputacións provinciais']);
  });

  it('preserves the order it receives rather than sorting, at every level', () => {
    // Deliberately out of name order: the server owns the Galician collation,
    // so a builder that repaired this would be a second source of truth.
    const unsortedTermos = [termo('t-b', 'Zamora'), termo('t-a', 'Ávila'), termo('t-c', 'Avión')];
    const unsortedOrganos = [
      organo('o-b', 'Zeta', 't-b'),
      organo('o-a', 'Alfa', 't-b'),
      organo('o-d', 'Omega', null),
      organo('o-c', 'Beta', null),
    ];

    const { roots, unclassified } = buildTaxonomiaView(unsortedTermos, unsortedOrganos);

    expect(roots.map((node) => node.name)).toEqual(['Zamora', 'Ávila', 'Avión']);
    expect(roots[0].organos.map((item) => item.name)).toEqual(['Zeta', 'Alfa']);
    expect(unclassified.map((item) => item.name)).toEqual(['Omega', 'Beta']);
  });

  it('keeps siblings in the order they arrive when grouped under a parent', () => {
    const first = termo('t-x', 'Primeiro', 't-1');
    const second = termo('t-y', 'Segundo', 't-1');

    const { roots } = buildTaxonomiaView([consellerias, second, first], []);

    expect(roots[0].children.map((node) => node.name)).toEqual(['Segundo', 'Primeiro']);
  });
});

describe('findTermoPath', () => {
  it('returns the chain of terms from a root down to the requested term', () => {
    const { roots } = buildTaxonomiaView([consellerias, sanidade, educacion, innovacion], []);

    expect(findTermoPath(roots, 't-4').map((node) => node.name)).toEqual([
      'Consellerías',
      'Consellería de Educación',
      'Axencia Galega de Innovación',
    ]);
  });

  it('returns an empty path when no term matches', () => {
    const { roots } = buildTaxonomiaView([consellerias, sanidade], []);

    expect(findTermoPath(roots, 't-deleted')).toEqual([]);
  });
});
