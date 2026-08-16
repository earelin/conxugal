import { Stack, Text, Title } from '@mantine/core';
import { Navigate, Outlet, useLocation, useMatch, useParams } from 'react-router';

import { type OrganoOutletContext, useOrgano } from '../../shared/entities/organo';
import { isHttpStatus } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { LoadingIndicator } from '../../shared/ui/LoadingIndicator';
import { familiesHeld } from './families';
import { FamilyTabs } from './FamilyTabs';

const copy = strings.organo;

/**
 * The frame every contract family's section mounts in: the Órgano's name, a tab
 * per family it holds, and the outlet that cedes the interior.
 *
 * It draws no contract and reads no contract list — one request, `GET
 * /api/organo/{id}`, carries the name, the tabs and the opening section's
 * summary. Which tabs to draw and which to redirect to are answered from the
 * `families` keys alone; every value is opaque here and reaches the section that
 * understands it as outlet context, so no section asks for it a second time and
 * neither slice imports the other.
 *
 * No subtitle under the name, unlike every other page: the tab bar is what says
 * what the page holds, and no field of the read carries a description.
 */
export function OrganoPage() {
  const { id = '' } = useParams();
  // Escaped on the way back out, because `useParams` hands it over decoded.
  const basePath = `/organo/${encodeURIComponent(id)}`;
  const { hash, search } = useLocation();
  // Read from the location rather than from a `handle` on the child route,
  // which would put the knowledge of what a family is into `app/`.
  // The splat lets a section's own deeper paths keep the family they sit under,
  // rather than reading as a family this build does not know and redirecting.
  const segment = useMatch('/organo/:id/:family/*')?.params.family ?? null;
  const { data: organo, error, isError, isFetching, isPending, refetch } = useOrgano(id);

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    // Neither name nor tabs: all three came from the one response that did not
    // arrive. An id the catalogue does not know is a third thing again, and
    // there is nothing to try again about it.
    return isHttpStatus(error, 404) ? (
      <ErrorAlert title={copy.notFoundTitle}>{copy.notFoundHelp}</ErrorAlert>
    ) : (
      <ErrorAlert
        title={copy.errorTitle}
        onRetry={() => {
          void refetch();
        }}
        retrying={isFetching}
      >
        {copy.errorHelp}
      </ErrorAlert>
    );
  }

  const held = familiesHeld(organo.families);

  // An answer, not a failure: no bar to draw and nothing to try again.
  if (held.length === 0) {
    return (
      <Stack gap="lg">
        <Title order={2}>{organo.name}</Title>
        <Stack gap={4}>
          <Text>{copy.noContracts}</Text>
          <Text c="dimmed">{copy.noContractsHelp}</Text>
        </Stack>
      </Stack>
    );
  }

  // The bare path, a family this build does not know, and one it knows but this
  // Órgano does not hold all land on the first family that has a tab: a URL
  // cannot conjure one, because the bar is built from the read. The query string
  // travels with it — a family's own state rides there.
  const active = held.find((family) => family.path === segment) ?? null;
  if (active === null) {
    return <Navigate to={{ pathname: `${basePath}/${held[0].path}`, search, hash }} replace />;
  }

  const context: OrganoOutletContext = { organo, family: organo.families[active.key] };

  return (
    <Stack gap="lg">
      <Title order={2}>{organo.name}</Title>
      <FamilyTabs basePath={basePath} held={held} active={active}>
        <Outlet context={context} />
      </FamilyTabs>
    </Stack>
  );
}
