import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Group, Modal, Stack, TextInput } from '@mantine/core';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { strings } from '../../shared/lib/strings';
import type { Termo } from './organos';
import type { TermoNode } from './taxonomiaTree';
import { useCreateTermo } from './termoMutations';
import { termoNameSchema, type TermoNameValues } from './termoName';
import { TermoParentSelect } from './TermoParentSelect';
import { isDuplicateSiblingName, type Refusal, termoRefusal } from './termoRefusal';
import { TermoRefusalAlert } from './TermoRefusalAlert';

const copy = strings.admin.organos.termo;

interface CreateTermoFormProps {
  roots: TermoNode[];
  /** The open term, offered as the parent so the common case is one click. */
  defaultParentId: string | null;
  onCreated: (created: Termo) => void;
  onCancel: () => void;
}

function CreateTermoForm({ roots, defaultParentId, onCreated, onCancel }: CreateTermoFormProps) {
  const [parentId, setParentId] = useState(defaultParentId);
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const createTermo = useCreateTermo();
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<TermoNameValues>({
    resolver: zodResolver(termoNameSchema),
    defaultValues: { name: '' },
  });

  function onSubmit({ name }: TermoNameValues) {
    setRefusal(null);
    createTermo.mutate(
      { name, parentId },
      {
        onSuccess: onCreated,
        onError: (error) => {
          // The dialog stays open either way, so the typed name survives and the
          // administrator corrects it rather than retyping it.
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
          placeholder={copy.createNamePlaceholder}
          required
          error={errors.name?.message}
          {...register('name')}
        />
        <TermoParentSelect
          roots={roots}
          label={copy.createParentLabel}
          placeholder={copy.createParentPlaceholder}
          description={copy.createParentHelp}
          value={parentId}
          // Both refusals a create can hit are about the parent — a name is only
          // duplicate among a given parent's children — so neither survives a
          // change of one. The name's own field error clears on the next
          // keystroke; this is the half that would otherwise stick.
          onChange={(id) => {
            setParentId(id);
            setRefusal(null);
            clearErrors('name');
          }}
        />
        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={onCancel}>
            {copy.cancel}
          </Button>
          <Button type="submit" loading={createTermo.isPending}>
            {copy.createSubmit}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

interface CreateTermoModalProps extends CreateTermoFormProps {
  opened: boolean;
}

export function CreateTermoModal({ opened, onCancel, ...formProps }: CreateTermoModalProps) {
  return (
    <Modal opened={opened} onClose={onCancel} title={copy.createTitle} radius="md">
      <CreateTermoForm onCancel={onCancel} {...formProps} />
    </Modal>
  );
}
