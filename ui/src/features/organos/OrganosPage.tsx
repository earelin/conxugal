import { Grid, Loader, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';

import { isHttpStatus } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import { findTermoPath, type TaxonomiaView } from '../../shared/lib/taxonomiaTree';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { AssignOrganoModal, type AssignTarget } from './catalogo/AssignOrganoModal';
import { TermoContentCard } from './catalogo/TermoContentCard';
import type { ImportAttempt } from './imports/importAttempt';
import { ImportToolbar } from './imports/ImportToolbar';
import { MarkOrganoModal } from './imports/MarkOrganoModal';
import { type Organo, useOrganosTaxonomia } from './organos';
import { DeleteTermoModal } from './taxonomia/DeleteTermoModal';
import { MoveTermoModal } from './taxonomia/MoveTermoModal';
import { RenameTermoModal } from './taxonomia/RenameTermoModal';
import { TaxonomiaTreeCard } from './taxonomia/TaxonomiaTreeCard';

type TermoAction = 'rename' | 'move' | 'delete';

/** A term-shape dialog and the term it was opened on, held as an id. */
type TermoRequest = { termoId: string; action: TermoAction };

/** Which half of the assign pair the entry point settled, held as an id. */
type AssignRequest = { kind: 'organo'; organoId: string } | { kind: 'termo'; termoId: string };

function resolveAssignTarget(
  view: TaxonomiaView<Organo>,
  request: AssignRequest,
): AssignTarget | null {
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
  // The Órgano whose mark is being confirmed, by the same discipline.
  const [markingId, setMarkingId] = useState<string | null>(null);
  // The one import this browser asked for, whether the switch or the toolbar
  // asked. Both feed one banner: a second attempt replaces the first, because
  // the guard admits one import at a time anyway.
  const [attempt, setAttempt] = useState<ImportAttempt | null>(null);
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
  // Matched against the open term, never taken on its own: an action held apart
  // from the term it was opened on would open its dialog on whichever term the
  // administrator selected next.
  const openAction = request !== null && request.termoId === openTermoId ? request.action : null;
  // Ids, not records, for the same reason: either record can be deleted by
  // another admin while the dialog is open, and one that stops resolving simply
  // unmounts it.
  const assignTarget = view && assigning ? resolveAssignTarget(view, assigning) : null;
  const marking = view?.catalogue.find((organo) => organo.id === markingId) ?? null;

  // What the section takes off the screen stays off it. Neither dialog closes
  // through its own handlers when what it is about stops resolving — a failed
  // read, or another admin's delete — it simply unmounts, and the read that
  // brings the record back is usually one nobody asked for: react-query refetches
  // both lists on every window focus. Dropping the request there is what keeps
  // that recovery from re-opening a dialog with no click behind it.
  if (request !== null && openAction === null) {
    setRequest(null);
  }
  if (assigning !== null && assignTarget === null) {
    setAssigning(null);
  }
  if (markingId !== null && marking === null) {
    setMarkingId(null);
  }

  // Every one of these is a control only the open term renders, so the id is
  // taken once here rather than re-checked in each handler.
  function forOpenTermo(use: (termoId: string) => void) {
    return () => {
      if (openTermoId !== null) {
        use(openTermoId);
      }
    };
  }

  const termoActions = {
    onRename: forOpenTermo((termoId) => setRequest({ termoId, action: 'rename' })),
    onMove: forOpenTermo((termoId) => setRequest({ termoId, action: 'move' })),
    onDelete: forOpenTermo((termoId) => setRequest({ termoId, action: 'delete' })),
    onAssign: forOpenTermo((termoId) => setAssigning({ kind: 'termo', termoId })),
  };

  function closeAction() {
    setRequest(null);
  }

  function afterDelete() {
    // The deleted term is gone from the next read; landing on its parent keeps
    // the administrator where they were working instead of at the worklist.
    setSelectedTermoId(openParentId);
  }

  return (
    <Stack gap="md">
      <Stack gap={0}>
        <Title order={2}>{strings.admin.organos.title}</Title>
        <Text c="dimmed">{strings.admin.organos.subtitle}</Text>
      </Stack>

      <ImportToolbar attempt={attempt} onAttempt={setAttempt} catalogue={view?.catalogue ?? []} />

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
              // A refused clear belongs to the pane it was attempted in, and
              // this card is otherwise reused across every selection — without
              // the key its alert would follow the reader to the next term.
              key={openTermoId ?? 'worklist'}
              openPath={openPath}
              unclassified={view.unclassified}
              termoActions={termoActions}
              onAssignOrgano={(organo) => setAssigning({ kind: 'organo', organoId: organo.id })}
              onMarkOrgano={(organo) => setMarkingId(organo.id)}
              onRefresh={refetch}
            />
          </Grid.Col>
        </Grid>
      )}

      {/* Mounted only while the Órgano resolves, so a catalogue that stops
          holding it takes the confirmation down with it. */}
      {marking && (
        <MarkOrganoModal
          opened
          organo={marking}
          onMarked={(marked) => {
            setAttempt(marked);
            setMarkingId(null);
          }}
          onCancel={() => setMarkingId(null)}
        />
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
