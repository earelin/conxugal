import { Button, Modal, Stack, TextInput } from '@mantine/core';
import { useState } from 'react';

import { strings } from '../../../shared/lib/strings';
import { TermoDialogFooter } from '../dialogs/TermoDialogFooter';
import { TermoRefusalAlert } from '../dialogs/TermoRefusalAlert';
import type { Termo } from '../organos';
import type { TermoNode } from '../taxonomiaTree';
import { useCreateTermo } from './termoMutations';
import type { TermoNameValues } from './termoName';
import { useTermoNameForm } from './termoNameForm';
import { TermoParentSelect } from './TermoParentSelect';

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
  const createTermo = useCreateTermo();
  const { form, refusal, clearRefusal, reportRefusal } = useTermoNameForm('');
  const {
    register,
    handleSubmit,
    clearErrors,
    formState: { errors },
  } = form;

  function onSubmit({ name }: TermoNameValues) {
    clearRefusal();
    // The dialog stays open on a refusal either way, so the typed name survives
    // and the administrator corrects it rather than retyping it.
    createTermo.mutate({ name, parentId }, { onSuccess: onCreated, onError: reportRefusal });
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
            clearRefusal();
            clearErrors('name');
          }}
        />
        <TermoDialogFooter onCancel={onCancel}>
          <Button type="submit" loading={createTermo.isPending}>
            {copy.createSubmit}
          </Button>
        </TermoDialogFooter>
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
