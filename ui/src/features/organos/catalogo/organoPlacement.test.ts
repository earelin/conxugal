import { describe, expect, it } from 'vitest';

import { strings } from '../../../shared/lib/strings';
import { buildTaxonomiaView } from '../../../shared/lib/taxonomiaTree';
import type { Organo, Termo } from '../organos';
import { organoPlacementLabel } from './organoPlacement';

const TAXONOMIA: Termo[] = [
  { id: 't-1', name: 'Consellerías', parentId: null },
  { id: 't-2', name: 'Consellería de Sanidade', parentId: 't-1' },
  { id: 't-3', name: 'Concellos', parentId: null },
];

function organo(termoId: string | null, active = true): Organo {
  return {
    id: 'o-1',
    name: 'Servizo Galego de Saúde',
    active,
    termoId,
    importable: false,
    importState: 'NEVER_STARTED',
  };
}

const { roots } = buildTaxonomiaView(TAXONOMIA, []);

describe('organoPlacementLabel', () => {
  it('reads a placement as the whole path down to it, not just the term', () => {
    expect(organoPlacementLabel(roots, organo('t-2'))).toBe(
      'Consellerías › Consellería de Sanidade',
    );
  });

  it('names a root term on its own', () => {
    expect(organoPlacementLabel(roots, organo('t-3'))).toBe('Concellos');
  });

  it('calls an unfiled Órgano unclassified', () => {
    expect(organoPlacementLabel(roots, organo(null))).toBe(strings.admin.organos.unclassified);
  });

  /**
   * The two reads are separate requests, so another administrator's delete can
   * land between them and leave a placement pointing at a term this browser
   * never saw. The builder files such an Órgano in the worklist; saying anything
   * else here would have the dialog and the worklist disagree about the same
   * record — and there is no name to show, because the term is not in the tree.
   */
  it('says unclassified for a placement whose term is not in the taxonomía', () => {
    expect(organoPlacementLabel(roots, organo('t-deleted'))).toBe(
      strings.admin.organos.unclassified,
    );
  });

  it('reads the same placement whether the Órgano is active or not', () => {
    expect(organoPlacementLabel(roots, organo('t-2', false))).toBe(
      organoPlacementLabel(roots, organo('t-2', true)),
    );
  });
});
