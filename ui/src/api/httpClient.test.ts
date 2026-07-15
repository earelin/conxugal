import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, HttpError } from './httpClient';

describe('apiFetch', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('resolves with the response when the request succeeds', async () => {
    const response = new Response(null, { status: 200 });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

    await expect(apiFetch('/api/data')).resolves.toBe(response);
  });

  it('requests JSON by sending an Accept: application/json header', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/api/data');

    const [, init] = fetchMock.mock.calls[0] as [RequestInfo | URL, RequestInit];
    expect(new Headers(init.headers).get('Accept')).toBe('application/json');
  });

  it('throws an HttpError carrying the status when the response is not ok', async () => {
    const response = new Response(null, { status: 401 });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

    const error = await apiFetch('/api/data').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(HttpError);
    expect((error as HttpError).status).toBe(401);
  });
});
