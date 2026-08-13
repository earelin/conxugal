import { describe, expect, it } from 'vitest';

import { HttpError, ProblemError } from '../../../shared/lib/httpClient';
import { strings } from '../../../shared/lib/strings';
import { placementRefusal } from './organoRefusal';

const copy = strings.admin.organos.assign;

function problem(type: string) {
  return new ProblemError(404, `urn:conxugal:problem-type:${type}`, null, type);
}

describe('placementRefusal', () => {
  it('names the Órgano when that is what went away', () => {
    expect(placementRefusal(problem('organo-not-found')).message).toBe(copy.organoNotFound);
  });

  it('names the target term when that is what went away', () => {
    expect(placementRefusal(problem('termo-not-found')).message).toBe(copy.termoNotFound);
  });

  it('falls back to the generic message for a problem type it does not know', () => {
    expect(placementRefusal(problem('termo-cycle')).message).toBe(copy.genericError);
  });

  it('falls back to the generic message for a refusal that carries no problem type', () => {
    expect(placementRefusal(new HttpError(500, 'boom')).message).toBe(copy.genericError);
  });

  it('falls back to the generic message for a transport failure', () => {
    expect(placementRefusal(new TypeError('Failed to fetch')).message).toBe(copy.genericError);
  });
});
