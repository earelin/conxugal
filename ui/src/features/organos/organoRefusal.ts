import { isHttpStatus, isProblemType } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import type { Refusal } from './termoRefusal';

const PROBLEM_TYPE = {
  organoNotFound: 'urn:conxugal:problem-type:organo-not-found',
  termoNotFound: 'urn:conxugal:problem-type:termo-not-found',
} as const;

const copy = strings.admin.organos.assign;
const markCopy = strings.admin.organos.contratosMenores;

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

/**
 * The refusals a mark or an unmark can hit. Only one is a rule: the Órgano is no
 * longer in the catalogue, which no retry fixes — the section has to be re-read.
 * A refused *import* never arrives here, because the mark applies whether or not
 * one starts and the server answers it as part of a success.
 */
export function markWriteRefusal(error: unknown): Refusal {
  const title = markCopy.write.errorTitle;
  if (isProblemType(error, PROBLEM_TYPE.organoNotFound)) {
    return { title, message: markCopy.write.notFound };
  }
  if (isHttpStatus(error, 403)) {
    return { title, message: markCopy.write.forbidden };
  }
  return { title, message: markCopy.write.generic };
}
