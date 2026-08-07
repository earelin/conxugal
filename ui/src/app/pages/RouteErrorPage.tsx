import { useRouteError } from 'react-router';

import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';

/**
 * A chunk that 404s because the app was redeployed under an open tab is the one
 * error here worth naming: it is nobody's fault, a reload fixes it, and the
 * message would otherwise read as a bug. Everything else the boundary catches is
 * a bug, and claiming a redeployment for it would send the reader off to reload
 * their way out of a crash that reloading cannot fix.
 */
function isStaleChunk(error: unknown): boolean {
  return error instanceof Error && /dynamically imported module/i.test(error.message);
}

export function RouteErrorPage() {
  const error = useRouteError();

  return (
    <ErrorAlert
      title={strings.routeError.title}
      // React caches a rejected lazy import for the lifetime of the page, so
      // re-rendering the route would replay the same failure — only a fresh
      // document can pick up the new asset names.
      onRetry={() => {
        globalThis.location.reload();
      }}
    >
      {isStaleChunk(error) ? strings.routeError.staleDeployment : strings.routeError.unexpected}
    </ErrorAlert>
  );
}
