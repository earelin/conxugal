import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { LoadingIndicator } from '../../shared/ui/LoadingIndicator';
import { useContratosMenores } from './contracts';
import { ContratosMenoresTable } from './ContratosMenoresTable';

const copy = strings.contratosMenores;

interface ContratosMenoresListProps {
  organoId: string;
  year: number;
}

/**
 * The selection's one read and the three things it can be doing.
 *
 * There is no fourth: a year is offered only where the Órgano has contracts in
 * it, so no choice a reader can make produces an empty page, and no empty state
 * is written for one that cannot arrive.
 */
export function ContratosMenoresList({ organoId, year }: ContratosMenoresListProps) {
  const { data, isPending, isError, isFetching, refetch } = useContratosMenores(organoId, year);

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

  return <ContratosMenoresTable contracts={data.items} />;
}
