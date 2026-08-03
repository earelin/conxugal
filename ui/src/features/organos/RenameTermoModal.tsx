import { Button, Group, Modal, Stack, TextInput } from '@mantine/core';

import { strings } from '../../shared/lib/strings';
import type { TermoNode } from './taxonomiaTree';
import { useRenameTermo } from './termoMutations';
import type { TermoNameValues } from './termoName';
import { useTermoNameForm } from './termoNameForm';
import { TermoRefusalAlert } from './TermoRefusalAlert';

const copy = strings.admin.organos.termo;

interface RenameTermoFormProps {
  termo: TermoNode;
  onRenamed: () => void;
  onCancel: () => void;
}

function RenameTermoForm({ termo, onRenamed, onCancel }: RenameTermoFormProps) {
  const renameTermo = useRenameTermo();
  const { form, refusal, clearRefusal, reportRefusal } = useTermoNameForm(termo.name);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = form;

  function onSubmit({ name }: TermoNameValues) {
    clearRefusal();
    renameTermo.mutate({ id: termo.id, name }, { onSuccess: onRenamed, onError: reportRefusal });
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
