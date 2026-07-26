import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../lib/httpClient';

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
