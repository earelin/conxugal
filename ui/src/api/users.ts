import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './httpClient';
import type { Role } from './currentUser';

export interface UserAccount {
  id: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface CreatedUser extends UserAccount {
  initialPassword: string;
}

export interface CreateUserInput {
  email: string;
  role: Role;
}

export const USERS_QUERY_KEY = ['users'] as const;

async function fetchUsers(): Promise<UserAccount[]> {
  const response = await apiFetch('/api/admin/users');
  return response.json() as Promise<UserAccount[]>;
}

export function useUsers() {
  return useQuery({ queryKey: USERS_QUERY_KEY, queryFn: fetchUsers });
}

async function createUser(input: CreateUserInput): Promise<CreatedUser> {
  const response = await apiFetch('/api/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  return response.json() as Promise<CreatedUser>;
}

export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createUser,
    // The response carries the one-time initial password; don't let the
    // mutation cache hold onto it after it loses its observer.
    gcTime: 0,
    onSuccess: (created) => {
      const account: UserAccount = {
        id: created.id,
        email: created.email,
        role: created.role,
        enabled: created.enabled,
        createdAt: created.createdAt,
        lastLoginAt: null,
      };
      queryClient.setQueryData<UserAccount[]>(USERS_QUERY_KEY, (users) => [
        ...(users ?? []),
        account,
      ]);
    },
  });
}

interface SetUserEnabledInput {
  id: string;
  enabled: boolean;
}

async function setUserEnabled({ id, enabled }: SetUserEnabledInput): Promise<UserAccount> {
  const response = await apiFetch(`/api/admin/users/${id}/enabled`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  });
  return response.json() as Promise<UserAccount>;
}

export function useSetUserEnabled() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: setUserEnabled,
    onSuccess: (updated) => {
      queryClient.setQueryData<UserAccount[]>(USERS_QUERY_KEY, (users) =>
        users?.map((user) => (user.id === updated.id ? updated : user)),
      );
    },
  });
}
