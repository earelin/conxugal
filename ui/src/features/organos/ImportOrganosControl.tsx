import { Alert, Button, Group, type MantineColor, Stack } from '@mantine/core';
import { IconCircleCheck, IconDownload, IconInfoCircle } from '@tabler/icons-react';
import { type ReactNode, useState } from 'react';

import { isHttpStatus, isProblemType } from '../../shared/lib/httpError';
import { singularOrPlural, type Word } from '../../shared/lib/plural';
import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import { type ImportOutcome, useImportOrganos } from './importOrganos';

const copy = strings.admin.organos.import;

/** The type the server gives a source failure, so it reads as more than a 500. */
const SOURCE_FAILURE = 'urn:conxugal:problem-type:organo-import-failed';

function counted(count: number, word: Word): string {
  return `${count} ${singularOrPlural(count, word)}`;
}

function outcomeCounts({ added, refreshed, deactivated }: ImportOutcome): string {
  return [
    counted(added, copy.added),
    counted(refreshed, copy.refreshed),
    counted(deactivated, copy.deactivated),
  ].join(' · ');
}

function failureMessage(error: unknown): string {
  if (isProblemType(error, SOURCE_FAILURE)) {
    return copy.errorSource;
  }
  return isHttpStatus(error, 403) ? copy.errorForbidden : copy.errorGeneric;
}

interface StatusAlertProps {
  color: MantineColor;
  icon: ReactNode;
  title: string;
  children: ReactNode;
  onDismiss: () => void;
}

/**
 * A report of what the last import did, announced politely and dismissible.
 *
 * `role="status"` rather than the assertive `role="alert"` Mantine defaults an
 * `Alert` to: this says what an administrator asked to be told and has no
 * business interrupting a screen reader mid-sentence. `ErrorAlert` keeps the
 * assertive role, which is the one case here that earns it. The policy lives in
 * one place because losing it is silent — an alert announced too forcefully
 * looks identical on screen.
 */
function StatusAlert({ color, icon, title, children, onDismiss }: StatusAlertProps) {
  return (
    <Alert
      color={color}
      role="status"
      title={title}
      icon={icon}
      withCloseButton
      closeButtonLabel={copy.dismiss}
      onClose={onDismiss}
    >
      {children}
    </Alert>
  );
}

interface OutcomeAlertProps {
  outcome: ImportOutcome;
  onDismiss: () => void;
  onRetry: () => void;
}

function OutcomeAlert({ outcome, onDismiss, onRetry }: OutcomeAlertProps) {
  switch (outcome.status) {
    case 'SUCCESS':
      return (
        <StatusAlert
          color="green"
          icon={<IconCircleCheck size={18} />}
          title={copy.successTitle}
          onDismiss={onDismiss}
        >
          {outcomeCounts(outcome)}
        </StatusAlert>
      );
    case 'ALREADY_RUNNING':
      return (
        <StatusAlert
          color="blue"
          icon={<IconInfoCircle size={18} />}
          title={copy.alreadyRunningTitle}
          onDismiss={onDismiss}
        >
          {copy.alreadyRunning}
        </StatusAlert>
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
        <OutcomeAlert outcome={outcome} onDismiss={importOrganos.reset} onRetry={runImport} />
      )}
    </Stack>
  );
}
