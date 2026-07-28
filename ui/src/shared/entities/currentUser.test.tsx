import { QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import nock from 'nock';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpError } from '../lib/httpClient';
import { createQueryClient } from '../lib/queryClient';
import { useLogout } from './currentUser';

const BASE_URL = 'http://localhost:3000';

function createWrapper() {
  const queryClient = createQueryClient();
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { Wrapper, queryClient };
}

describe('useLogout', () => {
  const replace = vi.fn();

  beforeEach(() => {
    nock.disableNetConnect();
    vi.stubGlobal('location', { ...window.location, replace });
  });

  afterEach(() => {
    nock.cleanAll();
    nock.enableNetConnect();
    replace.mockReset();
    vi.unstubAllGlobals();
  });

  it('navigates to /login once when the server replies with a followed 200', async () => {
    nock(BASE_URL).post('/logout').reply(200);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });
    result.current.mutate();

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
    expect(replace).toHaveBeenCalledOnce();
  });

  it('navigates to /login once when the server replies with an unfollowed 303', async () => {
    nock(BASE_URL).post('/logout').reply(303, undefined, { Location: '/login' });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });
    result.current.mutate();

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
    expect(replace).toHaveBeenCalledOnce();
  });

  it('navigates to /login once when the browser reports an opaque redirect', async () => {
    const opaqueRedirect = { status: 0, ok: false, type: 'opaqueredirect' } as unknown as Response;
    const fetchMock = vi.fn().mockResolvedValue(opaqueRedirect);
    vi.stubGlobal('fetch', fetchMock);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });
    result.current.mutate();

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
    expect(replace).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
      redirect: 'manual',
    });
  });

  it('posts a JSON content type with no Accept header and no CSRF token', async () => {
    const scope = nock(BASE_URL, {
      badheaders: ['accept', 'x-csrf-token'],
      reqheaders: { 'content-type': 'application/json' },
    })
      .post('/logout', {})
      .reply(200);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });
    result.current.mutate();

    await waitFor(() => expect(scope.isDone()).toBe(true));
  });

  it('rejects with HttpError and does not navigate when the server replies with a non-401 failure', async () => {
    nock(BASE_URL).post('/logout').reply(500);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });
    result.current.mutate();

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeInstanceOf(HttpError);
    expect((result.current.error as HttpError).status).toBe(500);
    expect(replace).not.toHaveBeenCalled();
  });

  it('rejects with HttpError carrying the status on a 401, leaving navigation to the shared session-loss handler', async () => {
    nock(BASE_URL).post('/logout').reply(401);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });
    result.current.mutate();

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeInstanceOf(HttpError);
    expect((result.current.error as HttpError).status).toBe(401);
    expect(replace).toHaveBeenCalledOnce();
    expect(replace).toHaveBeenCalledWith('/login');
  });

  it('does not navigate and surfaces the failure when the request itself fails', async () => {
    const networkFailure = new TypeError('Failed to fetch');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(networkFailure));

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });
    result.current.mutate();

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBe(networkFailure);
    expect(replace).not.toHaveBeenCalled();
  });

  it('does not clear the React Query cache on the success path', async () => {
    nock(BASE_URL).post('/logout').reply(200);

    const { Wrapper, queryClient } = createWrapper();
    queryClient.setQueryData(['currentUser'], { id: '1' });

    const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });
    result.current.mutate();

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
    expect(queryClient.getQueryData(['currentUser'])).toEqual({ id: '1' });
  });

  it('exposes mutate, isPending and isError for chrome to drive its in-flight and failed states', async () => {
    let resolveFetch: (response: Response) => void;
    const pendingFetch = new Promise<Response>((resolve) => {
      resolveFetch = resolve;
    });
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(pendingFetch));

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });

    expect(result.current.mutate).toBeTypeOf('function');
    expect(result.current.isPending).toBe(false);
    expect(result.current.isError).toBe(false);

    result.current.mutate();

    await waitFor(() => expect(result.current.isPending).toBe(true));

    resolveFetch!({ status: 200, ok: true } as Response);

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
  });
});
