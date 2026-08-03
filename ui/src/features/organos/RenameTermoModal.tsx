import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Group, Modal, Stack, TextInput } from '@mantine/core';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { strings } from '../../shared/lib/strings';
import type { TermoNode } from './taxonomiaTree';
import { useRenameTermo } from './termoMutations';
import { termoNameSchema, type TermoNameValues } from './termoName';
import { isDuplicateSiblingName, type Refusal, termoRefusal } from './termoRefusal';
import { TermoRefusalAlert } from './TermoRefusalAlert';

const copy = strings.admin.organos.termo;

interface RenameTermoFormProps {
  termo: TermoNode;
  onRenamed: () => void;
  onCancel: () => void;
}

function RenameTermoForm({ termo, onRenamed, onCancel }: RenameTermoFormProps) {
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const renameTermo = useRenameTermo();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<TermoNameValues>({
    resolver: zodResolver(termoNameSchema),
    defaultValues: { name: termo.name },
  });

  function onSubmit({ name }: TermoNameValues) {
    setRefusal(null);
    renameTermo.mutate(
      { id: termo.id, name },
      {
        onSuccess: onRenamed,
        onError: (error) => {
          if (isDuplicateSiblingName(error)) {
            setError('name', { message: copy.duplicateSiblingName });
          } else {
            setRefusal(termoRefusal(error));
          }
        },
      },
    );
  }

  return (
    <form onSubmit={(event) => void handleSubmit(onSubmit)(event)} noValidate>
      <Stack gap="md">
        <TermoRefusalAlert refusal={refusal} />
        <TextInput
          label={copy.nameLabel}
          required
          error={errors.name?.message}
          {...register('name')}
        />
        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={onCancel}>
            {copy.cancel}
          </Button>
          <Button type="submit" loading={renameTermo.isPending}>
            {copy.renameSubmit}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

interface RenameTermoModalProps extends RenameTermoFormProps {
  opened: boolean;
}

export function RenameTermoModal({ opened, onCancel, ...formProps }: RenameTermoModalProps) {
  return (
    <Modal opened={opened} onClose={onCancel} title={copy.renameTitle} radius="md">
      <RenameTermoForm onCancel={onCancel} {...formProps} />
    </Modal>
  );
}
