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
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
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

  it('navigates to /login when the server replies with a followed 200', async () => {
    nock(BASE_URL).post('/logout').reply(200);

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper() });
    result.current.mutate();

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
  });

  it('navigates to /login when the server replies with an unfollowed 303', async () => {
    nock(BASE_URL).post('/logout').reply(303, undefined, { Location: '/login' });

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper() });
    result.current.mutate();

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
  });

  it('navigates to /login when the browser reports an opaque redirect', async () => {
    const opaqueRedirect = { status: 0, ok: false, type: 'opaqueredirect' } as unknown as Response;
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(opaqueRedirect));

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper() });
    result.current.mutate();

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/login'));
  });

  it('posts a JSON content type with no Accept header and no CSRF token', async () => {
    const scope = nock(BASE_URL, {
      badheaders: ['accept', 'x-csrf-token'],
      reqheaders: { 'content-type': 'application/json' },
    })
      .post('/logout', {})
      .reply(200);

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper() });
    result.current.mutate();

    await waitFor(() => expect(scope.isDone()).toBe(true));
  });

  it('rejects with HttpError and does not navigate when the server replies with a non-401 failure', async () => {
    nock(BASE_URL).post('/logout').reply(500);

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper() });
    result.current.mutate();

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeInstanceOf(HttpError);
    expect((result.current.error as HttpError).status).toBe(500);
    expect(replace).not.toHaveBeenCalled();
  });

  it('rejects with HttpError carrying the status on a 401, leaving navigation to the shared session-loss handler', async () => {
    nock(BASE_URL).post('/logout').reply(401);

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper() });
    result.current.mutate();

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeInstanceOf(HttpError);
    expect((result.current.error as HttpError).status).toBe(401);
    expect(replace).toHaveBeenCalledOnce();
    expect(replace).toHaveBeenCalledWith('/login');
  });
});
