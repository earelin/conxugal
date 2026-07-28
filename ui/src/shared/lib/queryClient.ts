import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { HttpError } from './httpClient';

const TRANSIENT_HTTP_STATUSES = new Set([408, 429, 503, 504]);
const MAX_RETRIES = 3;

function retryOnTransientError(failureCount: number, error: unknown) {
  if (error instanceof HttpError && !TRANSIENT_HTTP_STATUSES.has(error.status)) {
    return false;
  }
  return failureCount < MAX_RETRIES;
}

const redirectOnceByClient = new WeakMap<QueryClient, () => void>();

export function createQueryClient(): QueryClient {
  let redirectingToLogin = false;

  function redirectOnce() {
    if (redirectingToLogin) {
      return;
    }
    redirectingToLogin = true;
    window.location.replace('/login');
  }

  function redirectToLoginOnSessionLoss(error: unknown) {
    if (error instanceof HttpError && error.status === 401) {
      redirectOnce();
    }
  }

  const client = new QueryClient({
    defaultOptions: { queries: { retry: retryOnTransientError } },
    queryCache: new QueryCache({ onError: redirectToLoginOnSessionLoss }),
    mutationCache: new MutationCache({ onError: redirectToLoginOnSessionLoss }),
  });

  redirectOnceByClient.set(client, redirectOnce);
  return client;
}

// Shared with callers outside the query/mutation cache (e.g. useLogout's own
// success path) so a redirect they trigger counts against the same
// once-only guard as the cache's session-loss handler above.
export function redirectToLogin(client: QueryClient): void {
  redirectOnceByClient.get(client)?.();
}

export const queryClient = createQueryClient();
