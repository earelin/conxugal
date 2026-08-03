import { describe, expect, it } from 'vitest';

import { HttpError, ProblemError } from './httpClient';
import { isHttpStatus, isProblemType } from './httpError';

const CYCLE = 'urn:conxugal:problem-type:termo-cycle';
const DUPLICATE_SIBLING = 'urn:conxugal:problem-type:duplicate-sibling-name';

describe('isHttpStatus', () => {
  it('matches an HttpError carrying the given status', () => {
    expect(isHttpStatus(new HttpError(403, 'forbidden'), 403)).toBe(true);
    expect(isHttpStatus(new HttpError(403, 'forbidden'), 409)).toBe(false);
  });

  it('rejects anything that is not an HttpError', () => {
    expect(isHttpStatus(new Error('network down'), 403)).toBe(false);
    expect(isHttpStatus(null, 403)).toBe(false);
  });
});

describe('isProblemType', () => {
  it('tells two refusals apart when they share a status', () => {
    const cycle = new ProblemError(409, CYCLE, 'Cannot move a term under itself', 'Cycle');
    const duplicate = new ProblemError(409, DUPLICATE_SIBLING, 'A sibling has that name', 'Dup');

    expect(isProblemType(cycle, CYCLE)).toBe(true);
    expect(isProblemType(cycle, DUPLICATE_SIBLING)).toBe(false);
    expect(isProblemType(duplicate, DUPLICATE_SIBLING)).toBe(true);
    expect(isProblemType(duplicate, CYCLE)).toBe(false);
  });

  it('rejects a plain HttpError, which carries no problem type at all', () => {
    expect(isProblemType(new HttpError(409, 'conflict'), CYCLE)).toBe(false);
  });

  it('rejects anything that is not an error from this client', () => {
    expect(isProblemType(new Error('network down'), CYCLE)).toBe(false);
    expect(isProblemType({ type: CYCLE }, CYCLE)).toBe(false);
    expect(isProblemType(undefined, CYCLE)).toBe(false);
  });

  it('still reports the status, so a ProblemError works with isHttpStatus too', () => {
    expect(isHttpStatus(new ProblemError(404, CYCLE, null, 'Not found'), 404)).toBe(true);
  });
});
