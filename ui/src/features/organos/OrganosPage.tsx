import { Grid, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';

import { isHttpStatus } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { DeleteTermoModal } from './DeleteTermoModal';
import { MoveTermoModal } from './MoveTermoModal';
import { useOrganosTaxonomia } from './organos';
import { RenameTermoModal } from './RenameTermoModal';
import { findTermoPath } from './taxonomiaTree';
import { TaxonomiaTreeCard } from './TaxonomiaTreeCard';
import { TermoContentCard } from './TermoContentCard';

type TermoAction = 'rename' | 'move' | 'delete';

export function OrganosPage() {
  // null selects the pinned worklist, which is where a freshly imported
  // catalogue lives and the one pane that always exists.
  const [selectedTermoId, setSelectedTermoId] = useState<string | null>(null);
  // The three writes act on whichever term is open, so they are held here
  // rather than in either pane: the tree row and the content header are two
  // ways into the same dialog, not two dialogs.
  const [action, setAction] = useState<TermoAction | null>(null);
  const { view, isPending, isFetching, isError, error, refetch } = useOrganosTaxonomia();

  // Resolved once, here, and handed to both panes so they cannot disagree about
  // what is open. The selected term can disappear under a refetch — another
  // admin deletes it while it is open — and an unresolvable id falls back to the
  // worklist; deriving that separately per pane is what would let the tree
  // highlight nothing while the content pane showed a list.
  const openPath =
    view && selectedTermoId !== null ? findTermoPath(view.roots, selectedTermoId) : [];
  const openTermoId = openPath.length > 0 ? selectedTermoId : null;
  const openTermo = openPath.at(-1) ?? null;
  const openParentId = openPath.at(-2)?.id ?? null;
  // Derived, never held: a term can stop resolving under the section — a failed
  // refetch, or another admin deleting it — and a held action would then re-open
  // its dialog by itself the moment the next term was selected.
  const openAction = openTermo === null ? null : action;

  const termoActions = {
    onRename: () => setAction('rename'),
    onMove: () => setAction('move'),
    onDelete: () => setAction('delete'),
  };

  function closeAction() {
    setAction(null);
  }

  function afterDelete() {
    // The deleted term is gone from the next read; landing on its parent keeps
    // the administrator where they were working instead of at the worklist.
    setSelectedTermoId(openParentId);
    setAction(null);
  }

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
              termoActions={termoActions}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, md: 7 }}>
            <TermoContentCard
              openPath={openPath}
              unclassified={view.unclassified}
              termoActions={termoActions}
            />
          </Grid.Col>
        </Grid>
      )}

      {view && openTermo && (
        <>
          <RenameTermoModal
            opened={openAction === 'rename'}
            termo={openTermo}
            onRenamed={closeAction}
            onCancel={closeAction}
          />
          <MoveTermoModal
            opened={openAction === 'move'}
            roots={view.roots}
            termo={openTermo}
            parentId={openParentId}
            onMoved={closeAction}
            onCancel={closeAction}
          />
          <DeleteTermoModal
            opened={openAction === 'delete'}
            termo={openTermo}
            onDeleted={afterDelete}
            onCancel={closeAction}
          />
        </>
      )}
    </Stack>
  );
}
