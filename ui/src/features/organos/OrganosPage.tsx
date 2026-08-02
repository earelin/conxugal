import { Grid, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';

import { isHttpStatus } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { useOrganosTaxonomia } from './organos';
import { TaxonomiaTreeCard } from './TaxonomiaTreeCard';
import { TermoContentCard } from './TermoContentCard';

export function OrganosPage() {
  // null selects the pinned worklist, which is where a freshly imported
  // catalogue lives and the one pane that always exists.
  const [selectedTermoId, setSelectedTermoId] = useState<string | null>(null);
  const { view, isPending, isError, error, refetch } = useOrganosTaxonomia();

  return (
    <Stack gap="md">
      <Stack gap={0}>
        <Title order={2}>{strings.admin.organos.title}</Title>
        <Text c="dimmed">{strings.admin.organos.subtitle}</Text>
      </Stack>

      {/* Section toolbar: the import trigger lands here. */}
      <Group justify="flex-end" />

      {isPending && <Loader />}

      {isError && (
        <ErrorAlert title={strings.admin.organos.errorTitle} onRetry={refetch}>
          {isHttpStatus(error, 403)
            ? strings.admin.organos.errorForbidden
            : strings.admin.organos.errorGeneric}
        </ErrorAlert>
      )}

      {!isError && view && (
        <Grid>
          <Grid.Col span={{ base: 12, md: 5 }}>
            <TaxonomiaTreeCard
              roots={view.roots}
              unclassified={view.unclassified}
              selectedTermoId={selectedTermoId}
              onSelect={setSelectedTermoId}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, md: 7 }}>
            <TermoContentCard
              roots={view.roots}
              unclassified={view.unclassified}
              selectedTermoId={selectedTermoId}
            />
          </Grid.Col>
        </Grid>
      )}
    </Stack>
  );
}
