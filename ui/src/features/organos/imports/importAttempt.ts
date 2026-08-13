import type { Organo } from '../organos';
import type { ImportRefusalKind, MarkOutcome } from './contratosMenores';

/**
 * The one import this browser asked for, as either the run doing the work or the
 * reason there is none.
 *
 * It is held in the section rather than read back, because the only run read is
 * by identifier: there is no *latest run* endpoint, so a reload loses this and
 * the banner starts empty again.
 */
export type ImportAttempt =
  | { kind: 'run'; runId: string }
  | {
      kind: 'refusal';
      refusal: ImportRefusalKind;
      /** The Órgano a mark named; null when a section-wide trigger was refused. */
      organo: Organo | null;
      /**
       * When this browser was told. A refusal writes no record, so there is no
       * server timestamp to report — only the moment the answer arrived.
       */
      refusedAt: Date;
    };

/**
 * What marking an Órgano amounted to, for the banner.
 *
 * Null only for a body carrying neither field, which the contract does not
 * admit: the mark itself succeeded and the table already shows it, so there is
 * genuinely nothing to report about an import that was never described.
 */
export function markAttempt(
  outcome: MarkOutcome,
  organo: Organo,
  refusedAt: Date,
): ImportAttempt | null {
  if (outcome.refusal !== null) {
    return { kind: 'refusal', refusal: outcome.refusal, organo, refusedAt };
  }
  return outcome.runId === null ? null : { kind: 'run', runId: outcome.runId };
}
