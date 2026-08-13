import { Button, Group, List, Modal, Stack, Text } from '@mantine/core';
import { IconDownload } from '@tabler/icons-react';
import { useState } from 'react';

import { strings } from '../../shared/lib/strings';
import { useMarkOrgano } from './contratosMenores';
import { type ImportAttempt, markAttempt } from './importAttempt';
import { markWriteRefusal } from './organoRefusal';
import type { Organo } from './organos';
import type { Refusal } from './termoRefusal';
import { TermoRefusalAlert } from './TermoRefusalAlert';

const copy = strings.admin.organos.contratosMenores;

interface MarkOrganoFormProps {
  organo: Organo;
  /** What the mark's own import did, for the banner the section already has. */
  onMarked: (attempt: ImportAttempt | null) => void;
  onCancel: () => void;
}

function MarkOrganoForm({ organo, onMarked, onCancel }: MarkOrganoFormProps) {
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const markOrgano = useMarkOrgano();

  function onSubmit() {
    setRefusal(null);
    markOrgano.mutate(organo.id, {
      onSuccess: (outcome) => onMarked(markAttempt(outcome, organo, new Date())),
      onError: (error) => setRefusal(markWriteRefusal(error)),
    });
  }

  return (
    <Stack gap="md">
      <TermoRefusalAlert refusal={refusal} />
      <Text>{copy.mark.intro(organo.name)}</Text>
      {/* What the mark costs, which is the reason this dialog exists at all: a
          multi-day walk that refuses every other import while it runs. */}
      <List size="sm" spacing="xs">
        <List.Item>{copy.mark.costDuration}</List.Item>
        <List.Item>{copy.mark.costGuard}</List.Item>
        <List.Item>{copy.mark.costRecentFirst}</List.Item>
        <List.Item>{copy.mark.costReversible}</List.Item>
      </List>
      <Group justify="flex-end" mt="sm">
        <Button variant="default" onClick={onCancel}>
          {copy.mark.cancel}
        </Button>
        <Button
          leftSection={<IconDownload size={16} />}
          loading={markOrgano.isPending}
          onClick={onSubmit}
        >
          {copy.mark.submit}
        </Button>
      </Group>
    </Stack>
  );
}

interface MarkOrganoModalProps extends MarkOrganoFormProps {
  opened: boolean;
}

/**
 * The confirmation that turning the switch on opens. R4 asks for a mark and not
 * for a dialog; it is here because `Marcar e importar` is the honest name for
 * what the write does, and because no other action in the admin area costs days.
 */
export function MarkOrganoModal({ opened, onCancel, ...formProps }: MarkOrganoModalProps) {
  return (
    <Modal opened={opened} onClose={onCancel} title={copy.mark.title} radius="md">
      <MarkOrganoForm onCancel={onCancel} {...formProps} />
    </Modal>
  );
}
