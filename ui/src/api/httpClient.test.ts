import { describe, expect, it, vi } from 'vitest';
import { apiFetch, HttpError } from './httpClient';

describe('apiFetch', () => {
  it('resolves with the response when the request succeeds', async () => {
    const response = new Response(null, { status: 200 });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

    await expect(apiFetch('/api/data')).resolves.toBe(response);
  });

  it('throws an HttpError carrying the status when the response is not ok', async () => {
    const response = new Response(null, { status: 401 });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

    const error = await apiFetch('/api/data').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(HttpError);
    expect((error as HttpError).status).toBe(401);
  });
});
