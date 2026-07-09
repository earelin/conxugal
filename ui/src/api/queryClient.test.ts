import { MutationObserver } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpError } from './httpClient';
import { queryClient } from './queryClient';

describe('queryClient', () => {
  const assign = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('location', { ...window.location, assign });
  });

  afterEach(() => {
    assign.mockReset();
    vi.unstubAllGlobals();
    queryClient.clear();
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

    expect(assign).toHaveBeenCalledWith('/login');
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

    expect(assign).not.toHaveBeenCalled();
  });

  it('navigates to /login when a mutation fails with a 401', async () => {
    const observer = new MutationObserver(queryClient, {
      mutationFn: () => {
        throw new HttpError(401, 'unauthorized');
      },
    });

    await observer.mutate().catch(() => undefined);

    expect(assign).toHaveBeenCalledWith('/login');
  });

  it('does not navigate when a mutation fails with a non-401 error', async () => {
    const observer = new MutationObserver(queryClient, {
      mutationFn: () => {
        throw new HttpError(500, 'server error');
      },
    });

    await observer.mutate().catch(() => undefined);

    expect(assign).not.toHaveBeenCalled();
  });
});
