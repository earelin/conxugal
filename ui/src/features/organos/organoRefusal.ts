import { isProblemType } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import type { Refusal } from './termoRefusal';

const PROBLEM_TYPE = {
  organoNotFound: 'urn:conxugal:problem-type:organo-not-found',
  termoNotFound: 'urn:conxugal:problem-type:termo-not-found',
} as const;

const copy = strings.admin.organos.assign;

/**
 * The two refusals a placement write can hit, keyed on the problem `type`. Both
 * are 404s — the status alone cannot say whether the Órgano or the target term
 * went away — and the difference matters, because one is about the record the
 * administrator picked and the other about where they aimed it.
 *
 * Both mean another administrator changed the taxonomía under this browser, so
 * neither is worth retrying: the section has to be re-read first, which is what
 * the alert's own refresh action does. Anything else — a 500 from a raced write,
 * a transport failure — falls through to the generic message rather than being
 * dressed up as a rule we can explain.
 */
export function placementRefusal(error: unknown): Refusal {
  if (isProblemType(error, PROBLEM_TYPE.organoNotFound)) {
    return { message: copy.organoNotFound };
  }
  if (isProblemType(error, PROBLEM_TYPE.termoNotFound)) {
    return { message: copy.termoNotFound };
  }
  return { message: copy.genericError };
}
