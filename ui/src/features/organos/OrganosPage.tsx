import { Grid, Loader, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';

import { isHttpStatus } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { AssignOrganoModal, type AssignTarget } from './AssignOrganoModal';
import { DeleteTermoModal } from './DeleteTermoModal';
import { ImportOrganosControl } from './ImportOrganosControl';
import { MoveTermoModal } from './MoveTermoModal';
import { useOrganosTaxonomia } from './organos';
import { RenameTermoModal } from './RenameTermoModal';
import { findTermoPath, type TaxonomiaView } from './taxonomiaTree';
import { TaxonomiaTreeCard } from './TaxonomiaTreeCard';
import { TermoContentCard } from './TermoContentCard';

type TermoAction = 'rename' | 'move' | 'delete';

/** A term-shape dialog and the term it was opened on, held as an id. */
type TermoRequest = { termoId: string; action: TermoAction };

/** Which half of the assign pair the entry point settled, held as an id. */
type AssignRequest = { kind: 'organo'; organoId: string } | { kind: 'termo'; termoId: string };

function resolveAssignTarget(view: TaxonomiaView, request: AssignRequest): AssignTarget | null {
  if (request.kind === 'organo') {
    const organo = view.catalogue.find((candidate) => candidate.id === request.organoId);
    return organo ? { kind: 'organo', organo } : null;
  }
  const termo = findTermoPath(view.roots, request.termoId).at(-1);
  return termo ? { kind: 'termo', termo } : null;
}

export function OrganosPage() {
  // null selects the pinned worklist, which is where a freshly imported
  // catalogue lives and the one pane that always exists.
  const [selectedTermoId, setSelectedTermoId] = useState<string | null>(null);
  // The three writes act on whichever term is open, so they are held here
  // rather than in either pane: the tree row and the content header are two
  // ways into the same dialog, not two dialogs.
  const [request, setRequest] = useState<TermoRequest | null>(null);
  // Ids, not records: the dialog is about whatever the section currently holds
  // under them, and re-resolving each render is what keeps it honest.
  const [assigning, setAssigning] = useState<AssignRequest | null>(null);
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
  // Matched against the open term, never taken on its own: a term can stop
  // resolving under the section — a failed refetch, or another admin deleting it
  // — which unmounts its dialog without any of the close handlers running. An
  // action held apart from its term would survive that and re-open its dialog on
  // whichever term the administrator selected next.
  const openAction = request !== null && request.termoId === openTermoId ? request.action : null;
  // Same rule for the assign dialog, and it earns it twice over: either record
  // can be deleted by another admin while the dialog is open, and an id that
  // stops resolving simply unmounts it.
  const assignTarget = view && assigning ? resolveAssignTarget(view, assigning) : null;

  // Every entry point is a control the open term renders, so there is always one
  // to name; the guard is what keeps the id out of the request rather than a
  // case the interface can reach.
  function open(action: TermoAction) {
    if (openTermoId !== null) {
      setRequest({ termoId: openTermoId, action });
    }
  }

  const termoActions = {
    onRename: () => open('rename'),
    onMove: () => open('move'),
    onDelete: () => open('delete'),
    onAssign: () => {
      if (openTermoId !== null) {
        setAssigning({ kind: 'termo', termoId: openTermoId });
      }
    },
  };

  function closeAction() {
    setRequest(null);
  }

  /**
   * The section-level retry, which is not the dialog's own refresh: a failed
   * read replaces the whole section, taking any open dialog with it, and
   * bringing the reader back to a screen they never asked to return to would be
   * a dialog re-opening by itself. The dialog's `Actualizar` keeps it open on
   * purpose, so the two refreshes stay separate handlers.
   */
  function retrySection() {
    setRequest(null);
    setAssigning(null);
    refetch();
  }

  function afterDelete() {
    // The deleted term is gone from the next read; landing on its parent keeps
    // the administrator where they were working instead of at the worklist.
    setSelectedTermoId(openParentId);
    setRequest(null);
  }

  return (
    <Stack gap="md">
      <Stack gap={0}>
        <Title order={2}>{strings.admin.organos.title}</Title>
        <Text c="dimmed">{strings.admin.organos.subtitle}</Text>
      </Stack>

      <ImportOrganosControl />

      {/* One read can fail while the other is still in flight; the failure is
          the thing to report, not a spinner alongside it. */}
      {isPending && !isError && <Loader />}

      {isError && (
        <ErrorAlert
          title={strings.admin.organos.errorTitle}
          onRetry={retrySection}
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
              // A refused clear belongs to the pane it was attempted in, and
              // this card is otherwise reused across every selection — without
              // the key its alert would follow the reader to the next term.
              key={openTermoId ?? 'worklist'}
              openPath={openPath}
              unclassified={view.unclassified}
              termoActions={termoActions}
              onAssignOrgano={(organo) => setAssigning({ kind: 'organo', organoId: organo.id })}
              onRefresh={refetch}
            />
          </Grid.Col>
        </Grid>
      )}

      {/* Mounted only while its target resolves, which is also what drops the
          picker's search and pending choice between two openings. */}
      {view && assignTarget && (
        <AssignOrganoModal
          opened
          view={view}
          target={assignTarget}
          onAssigned={() => setAssigning(null)}
          onCancel={() => setAssigning(null)}
          onRefresh={refetch}
        />
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
