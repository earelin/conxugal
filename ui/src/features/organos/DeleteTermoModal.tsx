import { Button, Group, Modal, Stack, Text } from '@mantine/core';
import { useState } from 'react';

import { strings } from '../../shared/lib/strings';
import type { TermoNode } from './taxonomiaTree';
import { useDeleteTermo } from './termoMutations';
import { deleteRefusal, hasChildrenRefusal, type Refusal } from './termoRefusal';
import { TermoRefusalAlert } from './TermoRefusalAlert';

const copy = strings.admin.organos.termo;

interface DeleteTermoFormProps {
  termo: TermoNode;
  onDeleted: () => void;
  onCancel: () => void;
}

function DeleteTermoForm({ termo, onDeleted, onCancel }: DeleteTermoFormProps) {
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const deleteTermo = useDeleteTermo();

  // The children are in the tree the section already renders, so the block is
  // explained on open rather than after a round trip that would refuse it anyway.
  const blocked = termo.children.length > 0;

  function onSubmit() {
    setRefusal(null);
    deleteTermo.mutate(termo.id, {
      onSuccess: onDeleted,
      onError: (error) => setRefusal(deleteRefusal(error, termo)),
    });
  }

  return (
    <Stack gap="md">
      <TermoRefusalAlert refusal={blocked ? hasChildrenRefusal(termo.children) : refusal} />
      <Text>{copy.deleteConfirm(termo.name)}</Text>
      {!blocked && (
        <Text size="sm" c="dimmed">
          {copy.deleteOrganosNote}
        </Text>
      )}
      <Group justify="flex-end" mt="sm">
        <Button variant="default" onClick={onCancel}>
          {copy.cancel}
        </Button>
        <Button color="red" onClick={onSubmit} disabled={blocked} loading={deleteTermo.isPending}>
          {copy.deleteSubmit}
        </Button>
      </Group>
    </Stack>
  );
}

interface DeleteTermoModalProps extends DeleteTermoFormProps {
  opened: boolean;
}

export function DeleteTermoModal({ opened, onCancel, ...formProps }: DeleteTermoModalProps) {
  return (
    <Modal opened={opened} onClose={onCancel} title={copy.deleteTitle} radius="md">
      <DeleteTermoForm onCancel={onCancel} {...formProps} />
    </Modal>
  );
}
