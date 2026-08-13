import { strings } from '../../shared/lib/strings';
import type { Organo } from './organos';

const copy = strings.admin.organos.contratosMenores;

/**
 * What one row says about its contratos menores, as the five states the mark and
 * the import state make between them — plus the row whose switch is blocked.
 *
 * `stale` is the one that earns its place: unmarking keeps everything already
 * stored, so an Órgano holding a million contracts must not render like one
 * never touched. `none` is reserved for a row with nothing stored at all.
 */
export type MarkState = 'ineligible' | 'none' | 'marked' | 'partial' | 'imported' | 'stale';

export function markState(organo: Organo): MarkState {
  if (!organo.active) {
    return 'ineligible';
  }
  if (organo.importable) {
    if (organo.importState === 'COMPLETE') {
      return 'imported';
    }
    return organo.importState === 'INCOMPLETE' ? 'partial' : 'marked';
  }
  return organo.importState === 'NEVER_STARTED' ? 'none' : 'stale';
}

/**
 * The tooltip a state carries. `stale` is the only one that has to look past its
 * own state: which of *partial* or *complete* it is holding is what keeps a
 * fifth badge unnecessary.
 */
export function markTooltip(organo: Organo, state: MarkState): string {
  switch (state) {
    case 'ineligible':
      return copy.tooltip.ineligible;
    case 'marked':
      return copy.tooltip.marked;
    case 'partial':
      return copy.tooltip.partial;
    case 'imported':
      return copy.tooltip.imported;
    case 'stale':
      return organo.importState === 'COMPLETE'
        ? copy.tooltip.staleComplete
        : copy.tooltip.stalePartial;
    default:
      return copy.tooltip.none;
  }
}

/** The *listed as marked* tally the count caption under a table carries. */
export function markedCount(organos: Organo[]): number {
  return organos.filter((organo) => organo.importable).length;
}
