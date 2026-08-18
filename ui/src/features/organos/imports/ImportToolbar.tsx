import { Button, Group, Stack, Text } from '@mantine/core';
import { IconCircleCheck, IconDownload, IconInfoCircle } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';

import { isHttpStatus, isProblemType } from '../../../shared/lib/httpError';
import { counted } from '../../../shared/lib/plural';
import { strings } from '../../../shared/lib/strings';
import { ErrorAlert } from '../../../shared/ui/ErrorAlert';
import { StatusAlert } from '../../../shared/ui/StatusAlert';
import { markWriteRefusal } from '../catalogo/organoRefusal';
import { type Organo, ORGANOS_QUERY_KEY } from '../organos';
import { useImportRun, useMarkOrgano, useStartContratosMenoresImport } from './contratosMenores';
import { type ImportAttempt, markAttempt } from './importAttempt';
import { type ImportOutcome, useImportOrganos } from './importOrganos';
import { ImportRunBanner } from './ImportRunBanner';
import { triggerRefusal } from './importRunOutcome';

const copy = strings.admin.organos.import;
const menores = strings.admin.organos.contratosMenores;

/** The type the server gives a source failure, so it reads as more than a 500. */
const SOURCE_FAILURE = 'urn:conxugal:problem-type:organo-import-failed';

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
          closeLabel={copy.dismiss}
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
          closeLabel={copy.dismiss}
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

interface ImportToolbarProps {
  /** The one import this session asked for, held by the section. */
  attempt: ImportAttempt | null;
  onAttempt: (attempt: ImportAttempt | null) => void;
  /** The catalogue, so a run can name the Órganos it covered. */
  catalogue: Organo[];
}

/**
 * The section's two import triggers and whatever the last of each reported.
 *
 * One component owns both because they are held by one guard: at most one import
 * runs across the whole system, so while either is going neither may be pressed,
 * and a rule that lives in two components is a rule that can disagree with
 * itself. It is also why the run read is here rather than in the banner — the
 * banner renders that read, but the buttons are what depend on it.
 *
 * The banners sit under the toolbar rather than replacing the section, so a
 * failed import leaves the catalogue and the taxonomía on screen: unlike a
 * failed *read*, there is nothing wrong with what is already displayed.
 */
export function ImportToolbar({ attempt, onAttempt, catalogue }: ImportToolbarProps) {
  const queryClient = useQueryClient();
  const importOrganos = useImportOrganos();
  const startImport = useStartContratosMenoresImport();
  const markOrgano = useMarkOrgano();
  const guardId = useId();
  const runId = attempt?.kind === 'run' ? attempt.runId : null;
  const run = useImportRun(runId);

  /**
   * The failure is mapped to its message once and held, rather than derived
   * from the mutation each render. `mutate` clears the mutation's error, so a
   * derived alert would unmount under the retry button being pressed — taking
   * the focused element with it — and the message would degrade to the generic
   * one for as long as the second attempt was in flight. Neither is cleared on
   * the way *into* an attempt, for the same reason.
   */
  const [failure, setFailure] = useState<string | null>(null);
  const [menoresFailure, setMenoresFailure] = useState<string | null>(null);
  /**
   * The attempt whose report has been read and closed. Held as the attempt
   * itself rather than as a flag, so the next one shows without anything having
   * to reset this — and, unlike clearing the attempt, closing the report does
   * not make the section forget that an import is running.
   */
  const [dismissed, setDismissed] = useState<ImportAttempt | null>(null);
  const outcome = importOrganos.data;
  const reported = attempt === dismissed ? null : attempt;

  // A run this browser just claimed is running until its read says otherwise:
  // the guard is held from the moment the trigger answered, not from the moment
  // the first read comes back.
  const running =
    importOrganos.isPending ||
    startImport.isPending ||
    markOrgano.isPending ||
    (runId !== null && (run.isPending || run.data?.state === 'IN_PROGRESS'));

  function runCatalogueImport() {
    importOrganos.mutate(undefined, {
      onSuccess: () => setFailure(null),
      onError: (error) => setFailure(failureMessage(error)),
    });
  }

  function runMenoresImport() {
    startImport.mutate(undefined, {
      onSuccess: (startedRunId) => {
        setMenoresFailure(null);
        onAttempt({ kind: 'run', runId: startedRunId });
      },
      onError: (error) => {
        const refusal = triggerRefusal(error);
        if (refusal === null) {
          setMenoresFailure(
            isHttpStatus(error, 403)
              ? menores.trigger.errorForbidden
              : menores.trigger.errorGeneric,
          );
          return;
        }
        // A refusal is not a failure: it answers the question that was asked,
        // and the banner is where an answer belongs.
        setMenoresFailure(null);
        onAttempt({ kind: 'refusal', refusal, organo: null, refusedAt: new Date() });
      },
    });
  }

  /**
   * Asks again for exactly what was refused — the mark's own import for a mark,
   * the sweep for the trigger. Only the guard refusal offers this: an ineligible
   * Órgano answers the same way until the catalogue or the mark moves.
   */
  function retryRefused() {
    const organo = attempt?.kind === 'refusal' ? attempt.organo : null;
    if (organo === null) {
      runMenoresImport();
      return;
    }
    markOrgano.mutate(organo.id, {
      onSuccess: (result) => onAttempt(markAttempt(result, organo, new Date())),
      // The write's own refusals are worth repeating rather than flattening into
      // "something went wrong": one of them says the Órgano is gone.
      onError: (error) => setMenoresFailure(markWriteRefusal(error).message),
    });
  }

  function refreshRun() {
    void run.refetch();
    // A settled run changes what the rows say about themselves, and the run read
    // is the only place this browser learns that it settled.
    void queryClient.invalidateQueries({ queryKey: ORGANOS_QUERY_KEY });
  }

  return (
    <Stack gap="sm">
      <Group justify="space-between" align="flex-start" wrap="wrap">
        <Text size="xs" c="dimmed" maw={420}>
          {menores.scopeNote}
        </Text>
        <Stack gap={4} align="flex-end">
          <Group gap="sm">
            <Button
              variant="default"
              leftSection={<IconDownload size={16} />}
              loading={startImport.isPending}
              disabled={running}
              aria-describedby={running ? guardId : undefined}
              onClick={runMenoresImport}
            >
              {startImport.isPending ? menores.trigger.running : menores.trigger.button}
            </Button>
            <Button
              leftSection={<IconDownload size={16} />}
              loading={importOrganos.isPending}
              disabled={running}
              aria-describedby={running ? guardId : undefined}
              onClick={runCatalogueImport}
            >
              {importOrganos.isPending ? copy.running : copy.button}
            </Button>
          </Group>
          {/* Why both buttons are dead, said on screen rather than on hover: a
              disabled button takes no focus and fires no pointer events, so a
              tooltip is the one affordance that could never reach a reader who
              is not using a mouse. */}
          {running && (
            <Text id={guardId} size="xs" c="dimmed" ta="right" maw={360}>
              {menores.trigger.guardHeld}
            </Text>
          )}
        </Stack>
      </Group>

      {failure !== null && (
        <ErrorAlert
          title={copy.errorTitle}
          onRetry={runCatalogueImport}
          retrying={importOrganos.isPending}
        >
          {failure}
        </ErrorAlert>
      )}

      {outcome && (
        <OutcomeAlert
          outcome={outcome}
          onDismiss={importOrganos.reset}
          onRetry={runCatalogueImport}
        />
      )}

      {menoresFailure !== null && (
        <ErrorAlert
          title={menores.trigger.errorTitle}
          onRetry={runMenoresImport}
          retrying={startImport.isPending}
        >
          {menoresFailure}
        </ErrorAlert>
      )}

      <ImportRunBanner
        attempt={reported}
        run={run}
        catalogue={catalogue}
        onRetry={retryRefused}
        retrying={startImport.isPending || markOrgano.isPending}
        onDismiss={() => setDismissed(attempt)}
        onRefresh={refreshRun}
      />
    </Stack>
  );
}
