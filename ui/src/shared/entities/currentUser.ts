import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch, HttpError } from '../lib/httpClient';
import { redirectToLogin } from '../lib/queryClient';

export const ROLES = ['USER', 'ADMIN'] as const;

export type Role = (typeof ROLES)[number];

export interface CurrentUser {
  id: string;
  email: string;
  role: Role;
  createdAt: string;
  lastLoginAt: string | null;
}

export const CURRENT_USER_QUERY_KEY = ['currentUser'] as const;

async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await apiFetch('/api/me');
  return response.json() as Promise<CurrentUser>;
}

export function useCurrentUser() {
  return useQuery({ queryKey: CURRENT_USER_QUERY_KEY, queryFn: fetchCurrentUser });
}

async function logout(): Promise<void> {
  const response = await fetch('/logout', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({}),
    redirect: 'manual',
  });
  if (response.status >= 400) {
    throw new HttpError(response.status, `Request failed with status ${response.status}`);
  }
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      redirectToLogin(queryClient);
    },
  });
}
