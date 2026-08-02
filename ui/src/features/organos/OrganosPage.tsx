import { Grid, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';

import { isHttpStatus } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { useOrganosTaxonomia } from './organos';
import { findTermoPath } from './taxonomiaTree';
import { TaxonomiaTreeCard } from './TaxonomiaTreeCard';
import { TermoContentCard } from './TermoContentCard';

export function OrganosPage() {
  // null selects the pinned worklist, which is where a freshly imported
  // catalogue lives and the one pane that always exists.
  const [selectedTermoId, setSelectedTermoId] = useState<string | null>(null);
  const { view, isPending, isFetching, isError, error, refetch } = useOrganosTaxonomia();

  // The selected term can disappear under a refetch — another admin deletes it
  // while it is open. Both panes read the same reconciled value so they cannot
  // disagree: without this the tree would highlight nothing (its id matches no
  // node) while the content pane fell back to the worklist, leaving the reader
  // on a list nothing claims to have selected.
  const openTermoId =
    view && selectedTermoId !== null && findTermoPath(view.roots, selectedTermoId).length > 0
      ? selectedTermoId
      : null;

  return (
    <Stack gap="md">
      <Stack gap={0}>
        <Title order={2}>{strings.admin.organos.title}</Title>
        <Text c="dimmed">{strings.admin.organos.subtitle}</Text>
      </Stack>

      {/* Section toolbar: the import trigger lands here. */}
      <Group justify="flex-end" />

      {/* One read can fail while the other is still in flight; the failure is
          the thing to report, not a spinner alongside it. */}
      {isPending && !isError && <Loader />}

      {isError && (
        <ErrorAlert
          title={strings.admin.organos.errorTitle}
          onRetry={refetch}
          retrying={isFetching}
        >
          {isHttpStatus(error, 403)
            ? strings.admin.organos.errorForbidden
            : strings.admin.organos.errorGeneric}
        </ErrorAlert>
      )}

      {view && (
        <Grid>
          <Grid.Col span={{ base: 12, md: 5 }}>
            <TaxonomiaTreeCard
              roots={view.roots}
              unclassified={view.unclassified}
              selectedTermoId={openTermoId}
              onSelect={setSelectedTermoId}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, md: 7 }}>
            <TermoContentCard
              roots={view.roots}
              unclassified={view.unclassified}
              selectedTermoId={openTermoId}
            />
          </Grid.Col>
        </Grid>
      )}
    </Stack>
  );
}
