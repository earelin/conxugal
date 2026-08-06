import { type ReactNode, useEffect } from 'react';
import { Outlet } from 'react-router';

import { useCurrentUser } from '../shared/entities/currentUser';
import { LoadingIndicator } from '../shared/ui/LoadingIndicator';

interface AdminRouteProps {
  fallback: ReactNode;
  /**
   * Starts fetching the code-split sections this guard covers. Called on mount
   * rather than once the role is known: withholding the subtree until the
   * session resolves would otherwise queue the section's chunk behind
   * `/api/me`, adding a round trip to every direct load of an admin URL. The
   * chunks hold no secrets — the API enforces ADMIN on its own — so fetching
   * them before the answer arrives costs a refused visitor bandwidth and
   * nothing else.
   */
  warm: () => void;
}

/**
 * Gates the admin route subtree on the session role. This is a client-side
 * convenience, not the real access control — GET /api/admin/* still enforces
 * ADMIN server-side regardless of what this renders. `fallback` and `warm` are
 * both injected, so the guard needs no opinion about what replaces a refused
 * subtree nor about which sections sit beneath it.
 */
export function AdminRoute({ fallback, warm }: AdminRouteProps) {
  const { data: currentUser, isPending } = useCurrentUser();

  useEffect(warm, [warm]);

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (currentUser?.role !== 'ADMIN') {
    return fallback;
  }

  return <Outlet />;
}
