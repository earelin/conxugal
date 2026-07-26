import { Center, Loader } from '@mantine/core';
import type { ReactNode } from 'react';
import { Outlet } from 'react-router';
import { useCurrentUser } from '../../shared/entities/currentUser';

/**
 * Gates the admin route subtree on the session role. This is a client-side
 * convenience, not the real access control — GET /api/admin/* still enforces
 * ADMIN server-side regardless of what this renders. `fallback` is injected by
 * the composition root (see `app/router.tsx`) so this feature never has to
 * reach up into the app layer to render the not-found page.
 */
export function AdminRoute({ fallback }: { fallback: ReactNode }) {
  const { data: currentUser, isPending } = useCurrentUser();

  if (isPending) {
    return (
      <Center py="xl">
        <Loader />
      </Center>
    );
  }

  if (currentUser?.role !== 'ADMIN') {
    return fallback;
  }

  return <Outlet />;
}
