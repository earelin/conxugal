import { Center, Loader } from '@mantine/core';
import { Outlet } from 'react-router';
import { useCurrentUser } from '../../shared/entities/currentUser';
import { NotFoundView } from '../../shared/ui/NotFoundView';

/**
 * Gates the admin route subtree on the session role. This is a client-side
 * convenience, not the real access control — GET /api/admin/* still enforces
 * ADMIN server-side regardless of what this renders.
 */
export function AdminRoute() {
  const { data: currentUser, isPending } = useCurrentUser();

  if (isPending) {
    return (
      <Center py="xl">
        <Loader />
      </Center>
    );
  }

  if (currentUser?.role !== 'ADMIN') {
    return <NotFoundView />;
  }

  return <Outlet />;
}
