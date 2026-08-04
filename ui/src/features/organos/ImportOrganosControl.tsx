import { Alert, Button, Group, Stack } from '@mantine/core';
import { IconCircleCheck, IconDownload, IconInfoCircle } from '@tabler/icons-react';

import { isProblemType } from '../../shared/lib/httpError';
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
  const outcome = importOrganos.data;

  function runImport() {
    importOrganos.mutate();
  }

  return (
    <Stack gap="sm">
      <Group justify="flex-end">
        <Button
          leftSection={<IconDownload size={16} />}
          loading={importOrganos.isPending}
          disabled={importOrganos.isPending}
          onClick={runImport}
        >
          {importOrganos.isPending ? copy.running : copy.button}
        </Button>
      </Group>

      {importOrganos.isError && (
        <ErrorAlert title={copy.errorTitle} onRetry={runImport} retrying={importOrganos.isPending}>
          {isProblemType(importOrganos.error, SOURCE_FAILURE)
            ? copy.errorSource
            : copy.errorGeneric}
        </ErrorAlert>
      )}

      {outcome?.status === 'SUCCESS' && (
        <Alert color="green" title={copy.successTitle} icon={<IconCircleCheck size={18} />}>
          {outcomeCounts(outcome)}
        </Alert>
      )}

      {outcome?.status === 'ALREADY_RUNNING' && (
        <Alert color="blue" title={copy.alreadyRunningTitle} icon={<IconInfoCircle size={18} />}>
          {copy.alreadyRunning}
        </Alert>
      )}
    </Stack>
  );
}
