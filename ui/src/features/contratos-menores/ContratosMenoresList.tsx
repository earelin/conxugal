import { Box, Stack } from '@mantine/core';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';

import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { LoadingIndicator } from '../../shared/ui/LoadingIndicator';
import { Pagination } from '../../shared/ui/Pagination';
import { useContratosMenores } from './contracts';
import { ContratosMenoresTable } from './ContratosMenoresTable';
import { type Selection, withSelection } from './selection';

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
  const { pathname, hash } = useLocation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { data, isPending, isPlaceholderData, isError, isFetching, refetch } = useContratosMenores(
    organoId,
    selection,
  );

  // The whole location a page sits at, hash included — the same shape the
  // section's own correction navigates to, so a fragment survives a write from
  // either of them.
  function pageAt(page: number) {
    return { pathname, search: `?${withSelection(searchParams, { page }).toString()}`, hash };
  }

  // Only before the first answer of this section's life. Every page after it
  // keeps the one already on screen, so nothing below unmounts to a spinner.
  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    return (
      <ErrorAlert
        title={copy.errorTitle}
        onRetry={() => void refetch()}
        // A failed query keeps reporting an error while it refetches, so without
        // this the alert sits unchanged after a click and reads as if the button
        // did nothing.
        retrying={isFetching}
      >
        {copy.errorHelp}
      </ErrorAlert>
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
    return <Navigate to={pageAt(data.totalPages)} replace />;
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
      {/* The four numbers of the envelope, in the base they arrive in: the page
          in the URL, the page sent to the API and the page shown here are one
          number, and nothing between the wire and the screen converts. */}
      <Pagination
        page={data.page}
        size={data.size}
        totalItems={data.totalItems}
        totalPages={data.totalPages}
        onPageChange={(page) => {
          void navigate(pageAt(page));
        }}
      />
    </Stack>
  );
}
