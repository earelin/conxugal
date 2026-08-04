import { Alert, Button, Group, Stack } from '@mantine/core';
import { IconCircleCheck, IconDownload, IconInfoCircle } from '@tabler/icons-react';
import { useState } from 'react';

import { isHttpStatus, isProblemType } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { type ImportOutcome, useImportOrganos } from './importOrganos';

const copy = strings.admin.organos.import;

/** The type the server gives a source failure, so it reads as more than a 500. */
const SOURCE_FAILURE = 'urn:conxugal:problem-type:organo-import-failed';

function plural(count: number, one: string, other: string): string {
  return `${count} ${count === 1 ? one : other}`;
}

function outcomeCounts({ added, refreshed, deactivated }: ImportOutcome): string {
  return [
    plural(added, copy.addedOne, copy.addedOther),
    plural(refreshed, copy.refreshedOne, copy.refreshedOther),
    plural(deactivated, copy.deactivatedOne, copy.deactivatedOther),
  ].join(' · ');
}

function failureMessage(error: unknown): string {
  if (isProblemType(error, SOURCE_FAILURE)) {
    return copy.errorSource;
  }
  return isHttpStatus(error, 403) ? copy.errorForbidden : copy.errorGeneric;
}

interface OutcomeAlertProps {
  outcome: ImportOutcome;
  onDismiss: () => void;
  onRetry: () => void;
}

/**
 * Both alerts are `role="status"`: they report what an administrator asked for
 * and has no reason to be interrupted mid-sentence by, unlike the assertive
 * `role="alert"` Mantine gives an `Alert` by default and `ErrorAlert` keeps.
 */
function OutcomeAlert({ outcome, onDismiss, onRetry }: OutcomeAlertProps) {
  switch (outcome.status) {
    case 'SUCCESS':
      return (
        <Alert
          color="green"
          role="status"
          title={copy.successTitle}
          icon={<IconCircleCheck size={18} />}
          withCloseButton
          closeButtonLabel={copy.dismiss}
          onClose={onDismiss}
        >
          {outcomeCounts(outcome)}
        </Alert>
      );
    case 'ALREADY_RUNNING':
      return (
        <Alert
          color="blue"
          role="status"
          title={copy.alreadyRunningTitle}
          icon={<IconInfoCircle size={18} />}
          withCloseButton
          closeButtonLabel={copy.dismiss}
          onClose={onDismiss}
        >
          {copy.alreadyRunning}
        </Alert>
      );
    // Unreachable against today's contract, and rendered rather than dropped
    // because the alternative failure mode is silent: a status this build does
    // not know would otherwise leave the button flickering and nothing said.
    default:
      return (
        <ErrorAlert title={copy.errorTitle} onRetry={onRetry}>
          {copy.errorGeneric}
        </ErrorAlert>
      );
  }
}

/**
 * The import trigger and whatever the last one reported.
 *
 * Three outcomes, three renderings, and the distinction is the point: a source
 * failure arrives as a 500 with its own problem type rather than as a body with
 * three zeroes, and "already running" is a normal answer that must not be
 * dressed as either a success or an error.
 *
 * The banner sits under the toolbar rather than replacing the section, so a
 * failed import leaves the catalogue and the taxonomía on screen — unlike a
 * failed *read*, there is nothing wrong with what is already displayed.
 */
export function ImportOrganosControl() {
  const importOrganos = useImportOrganos();
  /**
   * The failure is mapped to its message once and held, rather than derived
   * from the mutation each render. `mutate` clears the mutation's error, so a
   * derived alert would unmount under the retry button being pressed — taking
   * the focused element with it — and the message would degrade to the generic
   * one for as long as the second attempt was in flight.
   */
  const [failure, setFailure] = useState<string | null>(null);
  const outcome = importOrganos.data;

  function runImport() {
    importOrganos.mutate(undefined, {
      onSuccess: () => setFailure(null),
      onError: (error) => setFailure(failureMessage(error)),
    });
  }

  return (
    <Stack gap="sm">
      <Group justify="flex-end">
        <Button
          leftSection={<IconDownload size={16} />}
          loading={importOrganos.isPending}
          onClick={runImport}
        >
          {importOrganos.isPending ? copy.running : copy.button}
        </Button>
      </Group>

      {failure !== null && (
        <ErrorAlert title={copy.errorTitle} onRetry={runImport} retrying={importOrganos.isPending}>
          {failure}
        </ErrorAlert>
      )}

      {outcome && (
        <OutcomeAlert
          outcome={outcome}
          onDismiss={() => importOrganos.reset()}
          onRetry={runImport}
        />
      )}
    </Stack>
  );
}
