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

export function createQueryClient(): QueryClient {
  let redirectingToLogin = false;

  function redirectToLoginOnSessionLoss(error: unknown) {
    if (error instanceof HttpError && error.status === 401 && !redirectingToLogin) {
      redirectingToLogin = true;
      window.location.replace('/login');
    }
  }

  return new QueryClient({
    defaultOptions: { queries: { retry: retryOnTransientError } },
    queryCache: new QueryCache({ onError: redirectToLoginOnSessionLoss }),
    mutationCache: new MutationCache({ onError: redirectToLoginOnSessionLoss }),
  });
}

export const queryClient = createQueryClient();
