import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { HttpError } from './httpClient';

function redirectToLoginOnSessionLoss(error: unknown) {
  if (error instanceof HttpError && error.status === 401) {
    window.location.assign('/login');
  }
}

export const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: redirectToLoginOnSessionLoss }),
  mutationCache: new MutationCache({ onError: redirectToLoginOnSessionLoss }),
});
