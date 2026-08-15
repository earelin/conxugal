import { describe, expect, it } from 'vitest';

import {
  buildTaxonomiaView,
  findTermoPath,
  type Organo,
  pruneEmptyTermos,
  type Termo,
  termoPathLabel,
} from './taxonomiaTree';

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

  it('keeps the whole catalogue in the order the server sent it', () => {
    const { catalogue } = buildTaxonomiaView(
      [consellerias, sanidade],
      [vivenda, sergas, cunqueiro],
    );

    expect(catalogue).toEqual([vivenda, sergas, cunqueiro]);
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

describe('pruneEmptyTermos', () => {
  it('drops a term whose whole subtree holds no Organo', () => {
    const { roots } = buildTaxonomiaView(
      [consellerias, sanidade, educacion, innovacion, concellos],
      [sergas],
    );

    const pruned = pruneEmptyTermos(roots);

    expect(pruned.map((node) => node.name)).toEqual(['Consellerías']);
    expect(pruned[0].children.map((node) => node.name)).toEqual(['Consellería de Sanidade']);
  });

  it('keeps a term whose own Organos are absent but whose descendant has one', () => {
    const filed = organo('o-5', 'Axencia Galega de Innovación', 't-4');

    const pruned = pruneEmptyTermos(
      buildTaxonomiaView([consellerias, sanidade, educacion, innovacion], [filed]).roots,
    );

    // The whole chain down to the Órgano survives, intermediate terms included.
    expect(pruned.map((node) => node.name)).toEqual(['Consellerías']);
    expect(pruned[0].children.map((node) => node.name)).toEqual(['Consellería de Educación']);
    expect(pruned[0].children[0].children.map((node) => node.name)).toEqual([
      'Axencia Galega de Innovación',
    ]);
    expect(pruned[0].children[0].children[0].organos).toEqual([filed]);
  });

  it('keeps a term that holds Organos of its own and no child terms', () => {
    const pruned = pruneEmptyTermos(buildTaxonomiaView([concellos], []).roots);
    const withOrgano = pruneEmptyTermos(
      buildTaxonomiaView([concellos], [organo('o-6', 'Concello de Santiago', 't-5')]).roots,
    );

    expect(pruned).toEqual([]);
    expect(withOrgano.map((node) => node.name)).toEqual(['Concellos']);
  });

  it('prunes to nothing when no Organo is filed anywhere', () => {
    expect(pruneEmptyTermos(buildTaxonomiaView([], []).roots)).toEqual([]);
    expect(
      pruneEmptyTermos(buildTaxonomiaView([consellerias, sanidade, concellos], [vivenda]).roots),
    ).toEqual([]);
  });

  it('leaves the tree it was given untouched, at every level it prunes', () => {
    // The administration section renders this same tree from the same cached
    // read, so a prune that filtered in place would delete terms from it.
    const { roots } = buildTaxonomiaView([consellerias, sanidade, educacion, concellos], [sergas]);

    const pruned = pruneEmptyTermos(roots);

    expect(pruned.map((node) => node.name)).toEqual(['Consellerías']);
    expect(pruned[0].children.map((node) => node.name)).toEqual(['Consellería de Sanidade']);
    expect(roots.map((node) => node.name)).toEqual(['Consellerías', 'Concellos']);
    expect(roots[0].children.map((node) => node.name)).toEqual([
      'Consellería de Sanidade',
      'Consellería de Educación',
    ]);
  });
});

describe('termoPathLabel', () => {
  it('reads a path as one line, root first', () => {
    const { roots } = buildTaxonomiaView([consellerias, sanidade, educacion, innovacion], []);

    expect(termoPathLabel(findTermoPath(roots, 't-4'))).toBe(
      'Consellerías › Consellería de Educación › Axencia Galega de Innovación',
    );
  });

  it('reads an empty path as nothing at all', () => {
    expect(termoPathLabel([])).toBe('');
  });
});
