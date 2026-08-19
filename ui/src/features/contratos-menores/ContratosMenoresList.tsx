import { Box, Stack, VisuallyHidden } from '@mantine/core';
import { useEffect, useRef } from 'react';
import { Navigate } from 'react-router';

import { formatCount } from '../../shared/lib/number';
import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { LoadingIndicator } from '../../shared/ui/LoadingIndicator';
import { Pagination } from '../../shared/ui/Pagination';
import { type ContratosMenoresPage, useContratosMenores } from './contracts';
import { ContratosMenoresTable } from './ContratosMenoresTable';
import type { Selection } from './selection';
import { useSelectionUrl } from './selectionUrl';

const copy = strings.contratosMenores;

interface ContratosMenoresListProps {
  organoId: string;
  selection: Selection;
}

/**
 * The selection's one read, the page it answered with, and the control that
 * moves between pages.
 *
 * There is no empty state: a year is offered only where the Órgano has contracts
 * in it, so no choice a reader can make produces an empty page, and none is
 * written for one that cannot arrive. The one empty answer that *can* arrive is
 * a page past the end, which is not an empty selection but a request out of
 * range — and it is corrected rather than rendered.
 */
export function ContratosMenoresList({ organoId, selection }: ContratosMenoresListProps) {
  const { locationFor, choose } = useSelectionUrl();
  const { data, isPending, isPlaceholderData, isError, isFetching, refetch } = useContratosMenores(
    organoId,
    selection,
  );

  // The last answer that arrived, kept so a failed read does not take the
  // control down with the rows: a reader whose next page failed still has the
  // page that worked to go back to, which the alert's retry alone cannot offer
  // them.
  const answeredRef = useRef<ContratosMenoresPage | null>(null);
  useEffect(() => {
    if (data !== undefined && !isPlaceholderData) {
      answeredRef.current = data;
    }
  }, [data, isPlaceholderData]);

  function goTo(page: number) {
    // A jump can name the page already in force, which is not a step: writing
    // it would put an entry in the reader's history that goes nowhere, and on
    // page 1 would add a `page=1` the URL did not carry.
    if (page !== selection.page) {
      choose({ page });
    }
  }

  function paging(envelope: ContratosMenoresPage) {
    return (
      // The four numbers of the envelope, in the base they arrive in: the page
      // in the URL, the page sent to the API and the page shown here are one
      // number, and nothing between the wire and the screen converts.
      <Pagination
        page={envelope.page}
        size={envelope.size}
        totalItems={envelope.totalItems}
        totalPages={envelope.totalPages}
        onPageChange={goTo}
      />
    );
  }

  // Only before the first answer of this selection's life. A page of the same
  // year and ordering keeps the one already on screen, so nothing below unmounts
  // to a spinner; a change of year or ordering waits here, the count and the
  // page total being about to change too.
  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    return (
      <Stack gap="md">
        <ErrorAlert
          title={copy.errorTitle}
          onRetry={() => void refetch()}
          // A failed query keeps reporting an error while it refetches, so
          // without this the alert sits unchanged after a click and reads as if
          // the button did nothing.
          retrying={isFetching}
        >
          {copy.errorHelp}
        </ErrorAlert>
        {answeredRef.current !== null && paging(answeredRef.current)}
      </Stack>
    );
  }

  // Holding the previous answer is what keeps the control still while a reader
  // moves between pages — but a page past the end is empty by definition, so
  // after the clamp below there is nothing to hold: the dimmed table would be
  // the empty one this section never draws. That wait is the ordinary one.
  if (isPlaceholderData && data.items.length === 0) {
    return <LoadingIndicator />;
  }

  // A page past the end is answered rather than refused: an empty page carrying
  // the selection's true totals, which is what says plainly that the request was
  // out of range. Reachable by a shared link that has outlived its selection, or
  // by an import that stored rows between two requests. The clamp is the
  // client's precisely because clamping on the server would make the response
  // disagree with the request that produced it.
  //
  // Read against the page that was *asked for* rather than the one the answer
  // echoes back, so the URL and the control cannot disagree whatever comes back
  // — and only once the answer is this selection's, a held-over one knowing
  // nothing about how many pages the new selection has.
  //
  // A `totalPages` below 1 is no page to clamp to, and is not a state either:
  // the chooser offers only years that hold contracts, so an empty selection is
  // not something a reader can ask for.
  if (!isPlaceholderData && data.totalPages >= 1 && selection.page > data.totalPages) {
    return <Navigate to={locationFor({ page: data.totalPages })} replace />;
  }

  return (
    <Stack gap="md">
      {/* Dimmed and marked busy rather than replaced while the next page is
          fetched: moving between pages changes neither the stated count, the
          page total nor the ordering, so none of them may leave the screen while
          it happens — and the button just pressed keeps its focus. Only the
          window over the selection moves. */}
      <Box aria-busy={isPlaceholderData} opacity={isPlaceholderData ? 0.55 : 1}>
        <ContratosMenoresTable contracts={data.items} />
      </Box>
      {/* `aria-busy` is not announced — it only quietens a region that is
          already live — and holding focus on the pressed button is exactly what
          removes the arrival a remount used to announce. This says which page a
          reader is now on, which is the whole of what changed.

          `aria-live` rather than `role="status"`, which is the same thing named:
          the two statements the section makes about itself are the statuses here,
          and a third would be counted among them by anything asking what this
          section says. */}
      <VisuallyHidden aria-live="polite" aria-atomic>
        {isPlaceholderData
          ? strings.loading
          : copy.pageAnnounced(formatCount(data.page), formatCount(data.totalPages))}
      </VisuallyHidden>
      {paging(data)}
    </Stack>
  );
}
