import { describe, expect, it } from 'vitest';

import { strings } from '../../shared/lib/strings';
import { markedCount, markState, markTooltip } from './importMark';
import type { ContratosMenoresImportState, Organo } from './organos';

const copy = strings.admin.organos.contratosMenores;

function organo(
  importable: boolean,
  importState: ContratosMenoresImportState,
  active = true,
): Organo {
  return {
    id: 'o-1',
    name: 'Servizo Galego de Saúde',
    active,
    termoId: null,
    importable,
    importState,
  };
}

describe('markState', () => {
  it('reads a newly discovered Órgano as holding nothing at all', () => {
    expect(markState(organo(false, 'NEVER_STARTED'))).toBe('none');
  });

  it('separates a marked Órgano that has stored nothing from one half-loaded', () => {
    expect(markState(organo(true, 'NEVER_STARTED'))).toBe('marked');
    expect(markState(organo(true, 'INCOMPLETE'))).toBe('partial');
  });

  it('never reads a half-loaded Órgano as the complete one', () => {
    expect(markState(organo(true, 'INCOMPLETE'))).not.toBe(markState(organo(true, 'COMPLETE')));
    expect(markState(organo(true, 'COMPLETE'))).toBe('imported');
  });

  it('keeps an unmarked Órgano holding contracts distinct from one holding none', () => {
    // The whole reason the fourth badge exists: unmarking retains what was
    // stored, so these two rows must not read alike.
    expect(markState(organo(false, 'INCOMPLETE'))).toBe('stale');
    expect(markState(organo(false, 'COMPLETE'))).toBe('stale');
    expect(markState(organo(false, 'NEVER_STARTED'))).toBe('none');
  });

  it('blocks an inactive Órgano whatever its mark says, because eligibility needs both', () => {
    expect(markState(organo(true, 'INCOMPLETE', false))).toBe('ineligible');
    expect(markState(organo(false, 'NEVER_STARTED', false))).toBe('ineligible');
  });
});

describe('markTooltip', () => {
  it('says which stored state an unmarked Órgano is holding, so no fifth badge is needed', () => {
    const partial = organo(false, 'INCOMPLETE');
    const complete = organo(false, 'COMPLETE');

    expect(markTooltip(partial, 'stale')).toBe(copy.tooltip.stalePartial);
    expect(markTooltip(complete, 'stale')).toBe(copy.tooltip.staleComplete);
    expect(markTooltip(partial, 'stale')).not.toBe(markTooltip(complete, 'stale'));
  });

  it('explains a blocked switch rather than leaving it silently unusable', () => {
    expect(markTooltip(organo(false, 'NEVER_STARTED', false), 'ineligible')).toBe(
      copy.tooltip.ineligible,
    );
  });
});

describe('markedCount', () => {
  it('counts the marked Órganos, whatever they have stored', () => {
    const organos = [
      organo(true, 'NEVER_STARTED'),
      organo(true, 'COMPLETE'),
      organo(false, 'INCOMPLETE'),
    ];

    expect(markedCount(organos)).toBe(2);
  });

  it('counts nothing in a list where nobody opted in', () => {
    expect(markedCount([organo(false, 'NEVER_STARTED')])).toBe(0);
    expect(markedCount([])).toBe(0);
  });
});
