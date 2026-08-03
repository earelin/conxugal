import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { apiFetch, HttpError, ProblemError } from './httpClient';

const BASE_URL = 'http://localhost:3000';

describe('apiFetch', () => {
  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
  });

  it('resolves with the response when the request succeeds', async () => {
    nock(BASE_URL).get('/api/data').reply(200);

    const response = await apiFetch('/api/data');

    expect(response.status).toBe(200);
    expect(response.ok).toBe(true);
  });

  it('requests JSON by sending an Accept: application/json header', async () => {
    const scope = nock(BASE_URL, { reqheaders: { accept: 'application/json' } })
      .get('/api/data')
      .reply(200);

    await apiFetch('/api/data');

    expect(scope.isDone()).toBe(true);
  });

  it('preserves caller-supplied headers passed as a Headers instance', async () => {
    const scope = nock(BASE_URL, {
      reqheaders: { 'x-csrf-token': 'token', accept: 'application/json' },
    })
      .get('/api/data')
      .reply(200);

    await apiFetch('/api/data', { headers: new Headers({ 'X-CSRF-TOKEN': 'token' }) });

    expect(scope.isDone()).toBe(true);
  });

  it('does not override an explicit caller-supplied Accept header', async () => {
    const scope = nock(BASE_URL, { reqheaders: { accept: 'text/html' } })
      .get('/api/data')
      .reply(200);

    await apiFetch('/api/data', { headers: { Accept: 'text/html' } });

    expect(scope.isDone()).toBe(true);
  });

  it('preserves caller-supplied headers passed as a tuple array', async () => {
    const scope = nock(BASE_URL, {
      reqheaders: { 'x-csrf-token': 'token', accept: 'application/json' },
    })
      .get('/api/data')
      .reply(200);

    await apiFetch('/api/data', { headers: [['X-CSRF-TOKEN', 'token']] });

    expect(scope.isDone()).toBe(true);
  });

  it('throws an HttpError carrying the status when the response is not ok', async () => {
    nock(BASE_URL).get('/api/data').reply(401);

    const error = await apiFetch('/api/data').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(HttpError);
    expect((error as HttpError).status).toBe(401);
  });

  it('throws a ProblemError carrying the type and detail of a problem+json refusal', async () => {
    nock(BASE_URL).get('/api/data').reply(
      409,
      {
        type: 'urn:conxugal:problem-type:termo-cycle',
        title: 'Term Cycle',
        status: 409,
        detail: 'Cannot move term a under one of its descendants: b',
      },
      { 'Content-Type': 'application/problem+json' },
    );

    const error = await apiFetch('/api/data').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ProblemError);
    expect((error as ProblemError).type).toBe('urn:conxugal:problem-type:termo-cycle');
    expect((error as ProblemError).status).toBe(409);
    expect((error as ProblemError).detail).toBe(
      'Cannot move term a under one of its descendants: b',
    );
  });

  it('keeps a ProblemError an HttpError, so the retry and session-expiry policies still see it', async () => {
    nock(BASE_URL)
      .get('/api/data')
      .reply(
        401,
        { type: 'urn:conxugal:problem-type:unauthorized', title: 'Unauthorized', status: 401 },
        { 'Content-Type': 'application/problem+json' },
      );

    const error = await apiFetch('/api/data').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(HttpError);
    expect((error as ProblemError).detail).toBeNull();
  });

  it('degrades to a plain HttpError when the failing response is not problem+json', async () => {
    nock(BASE_URL)
      .get('/api/data')
      .reply(409, { message: 'conflict' }, { 'Content-Type': 'application/json' });

    const error = await apiFetch('/api/data').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(HttpError);
    expect(error).not.toBeInstanceOf(ProblemError);
    expect((error as HttpError).status).toBe(409);
  });

  it('degrades to a plain HttpError when a problem+json body does not parse', async () => {
    nock(BASE_URL)
      .get('/api/data')
      .reply(409, 'not json at all', { 'Content-Type': 'application/problem+json' });

    const error = await apiFetch('/api/data').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(HttpError);
    expect(error).not.toBeInstanceOf(ProblemError);
    expect((error as HttpError).status).toBe(409);
  });
});
