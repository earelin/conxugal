import { Button, Group, Modal, Stack, Text } from '@mantine/core';
import { useState } from 'react';

import { strings } from '../../shared/lib/strings';
import { findTermoPath, type TermoNode } from './taxonomiaTree';
import { useMoveTermo } from './termoMutations';
import { TermoParentSelect } from './TermoParentSelect';
import { cycleRefusal, moveRefusal, type Refusal } from './termoRefusal';
import { TermoRefusalAlert } from './TermoRefusalAlert';

const copy = strings.admin.organos.termo;

interface MoveTermoFormProps {
  roots: TermoNode[];
  termo: TermoNode;
  /** Where the term sits now, so the field opens showing its current place. */
  parentId: string | null;
  onMoved: () => void;
  onCancel: () => void;
}

function MoveTermoForm({ roots, termo, parentId, onMoved, onCancel }: MoveTermoFormProps) {
  const [targetId, setTargetId] = useState(parentId);
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const moveTermo = useMoveTermo();

  const target = targetId === null ? null : (findTermoPath(roots, targetId).at(-1) ?? null);
  // The term itself and its own descendants stay in the picker and are refused
  // here instead, so the rule explains itself where the administrator looks for
  // the destination rather than going missing from the list.
  const cycles =
    target !== null &&
    (target.id === termo.id || findTermoPath(termo.children, target.id).length > 0);

  function onSubmit() {
    setRefusal(null);
    moveTermo.mutate(
      { id: termo.id, parentId: targetId },
      {
        onSuccess: onMoved,
        onError: (error) => setRefusal(moveRefusal(error, termo, target)),
      },
    );
  }

  return (
    <Stack gap="md">
      <TermoRefusalAlert refusal={cycles ? cycleRefusal(termo, target) : refusal} />
      <Stack gap={2}>
        <Text size="sm" c="dimmed">
          {copy.moveTermoLabel}
        </Text>
        <Text fw={600}>{termo.name}</Text>
      </Stack>
      <TermoParentSelect
        roots={roots}
        label={copy.moveParentLabel}
        rootOptionLabel={copy.moveParentRoot}
        required
        value={targetId}
        // A refusal is about the destination that was submitted. Leaving it up
        // over a new one would assert a clash nothing has tested for, with the
        // primary enabled to act on it.
        onChange={(id) => {
          setTargetId(id);
          setRefusal(null);
        }}
      />
      <Group justify="flex-end" mt="sm">
        <Button variant="default" onClick={onCancel}>
          {copy.cancel}
        </Button>
        <Button onClick={onSubmit} disabled={cycles} loading={moveTermo.isPending}>
          {copy.moveSubmit}
        </Button>
      </Group>
    </Stack>
  );
}

interface MoveTermoModalProps extends MoveTermoFormProps {
  opened: boolean;
}

export function MoveTermoModal({ opened, onCancel, ...formProps }: MoveTermoModalProps) {
  return (
    <Modal opened={opened} onClose={onCancel} title={copy.moveTitle} radius="md">
      <MoveTermoForm onCancel={onCancel} {...formProps} />
    </Modal>
  );
}
