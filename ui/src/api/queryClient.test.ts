import { MutationObserver } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpError } from './httpClient';
import { queryClient, resetSessionRedirectGuard } from './queryClient';

describe('queryClient', () => {
  const replace = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('location', { ...window.location, replace });
  });

  afterEach(() => {
    replace.mockReset();
    vi.unstubAllGlobals();
    queryClient.clear();
    resetSessionRedirectGuard();
  });

  it('navigates to /login when a query fails with a 401', async () => {
    await queryClient
      .fetchQuery({
        queryKey: ['session-loss-query'],
        queryFn: () => {
          throw new HttpError(401, 'unauthorized');
        },
        retry: false,
      })
      .catch(() => undefined);

    expect(replace).toHaveBeenCalledWith('/login');
  });

  it('does not navigate when a query fails with a non-401 error', async () => {
    await queryClient
      .fetchQuery({
        queryKey: ['other-failure-query'],
        queryFn: () => {
          throw new HttpError(500, 'server error');
        },
        retry: false,
      })
      .catch(() => undefined);

    expect(replace).not.toHaveBeenCalled();
  });

  it('navigates to /login when a mutation fails with a 401', async () => {
    const observer = new MutationObserver(queryClient, {
      mutationFn: () => {
        throw new HttpError(401, 'unauthorized');
      },
    });

    await observer.mutate().catch(() => undefined);

    expect(replace).toHaveBeenCalledWith('/login');
  });

  it('does not navigate when a mutation fails with a non-401 error', async () => {
    const observer = new MutationObserver(queryClient, {
      mutationFn: () => {
        throw new HttpError(500, 'server error');
      },
    });

    await observer.mutate().catch(() => undefined);

    expect(replace).not.toHaveBeenCalled();
  });

  it('navigates only once when several queries fail with a 401 concurrently', async () => {
    const failWith401 = () => {
      throw new HttpError(401, 'unauthorized');
    };

    await Promise.all([
      queryClient
        .fetchQuery({ queryKey: ['session-loss-query-a'], queryFn: failWith401, retry: false })
        .catch(() => undefined),
      queryClient
        .fetchQuery({ queryKey: ['session-loss-query-b'], queryFn: failWith401, retry: false })
        .catch(() => undefined),
    ]);

    expect(replace).toHaveBeenCalledTimes(1);
  });

  it('does not retry a query that fails with a 401 under the default retry policy', async () => {
    const queryFn = vi.fn().mockRejectedValue(new HttpError(401, 'unauthorized'));

    await queryClient
      .fetchQuery({ queryKey: ['session-loss-default-retry-query'], queryFn })
      .catch(() => undefined);

    expect(queryFn).toHaveBeenCalledTimes(1);
  });

  it('retries a query that fails with a transient error', async () => {
    const queryFn = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce(new HttpError(503, 'unavailable'))
      .mockResolvedValueOnce('ok');

    const result = await queryClient.fetchQuery({
      queryKey: ['transient-failure-query'],
      queryFn,
      retryDelay: 0,
    });

    expect(result).toBe('ok');
    expect(queryFn).toHaveBeenCalledTimes(2);
  });

  it('does not retry a query that fails with a non-transient error', async () => {
    const queryFn = vi.fn().mockRejectedValue(new HttpError(400, 'bad request'));

    await queryClient
      .fetchQuery({ queryKey: ['non-transient-failure-query'], queryFn })
      .catch(() => undefined);

    expect(queryFn).toHaveBeenCalledTimes(1);
  });
});
