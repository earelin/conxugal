import {
  ActionIcon,
  Alert,
  Button,
  CopyButton,
  Group,
  Modal,
  Select,
  Stack,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { IconAlertTriangle, IconCheck, IconCopy } from '@tabler/icons-react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { ROLES } from '../../shared/entities/currentUser';
import { type CreatedUser, useCreateUser } from './users';
import { isHttpStatus } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';

function PasswordReveal({ created, onDone }: { created: CreatedUser; onDone: () => void }) {
  const passwordInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    passwordInputRef.current?.focus();
  }, []);

  return (
    <Stack gap="md">
      <Alert color="yellow" icon={<IconAlertTriangle size={18} />}>
        {strings.admin.users.passwordWarning}
      </Alert>
      <TextInput
        ref={passwordInputRef}
        label={strings.admin.users.passwordLabel}
        value={created.initialPassword}
        readOnly
        rightSection={
          <CopyButton value={created.initialPassword}>
            {({ copied, copy }) => (
              <Tooltip label={copied ? strings.admin.users.copied : strings.admin.users.copy}>
                <ActionIcon
                  variant="subtle"
                  color={copied ? 'green' : 'gray'}
                  onClick={copy}
                  aria-label={strings.admin.users.copy}
                >
                  {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                </ActionIcon>
              </Tooltip>
            )}
          </CopyButton>
        }
      />
      <Group justify="flex-end">
        <Button onClick={onDone}>{strings.admin.users.done}</Button>
      </Group>
    </Stack>
  );
}

const createUserSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, strings.admin.users.emailRequired)
    .pipe(z.email(strings.admin.users.emailInvalid)),
  role: z.enum(ROLES),
});

type CreateUserFormValues = z.infer<typeof createUserSchema>;

interface CreateUserFormProps {
  onCreated: (created: CreatedUser) => void;
  onCancel: () => void;
}

function CreateUserForm({ onCreated, onCancel }: CreateUserFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const createUser = useCreateUser();
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: { email: '', role: ROLES[0] },
  });

  function onSubmit(values: CreateUserFormValues) {
    setFormError(null);
    createUser.mutate(values, {
      onSuccess: onCreated,
      onError: (error) => {
        if (isHttpStatus(error, 409)) {
          setError('email', { message: strings.admin.users.duplicateEmailError });
        } else {
          setFormError(strings.admin.users.createGenericError);
        }
      },
    });
  }

  return (
    <form onSubmit={(event) => void handleSubmit(onSubmit)(event)} noValidate>
      <Stack gap="md">
        {formError && <Alert color="red">{formError}</Alert>}
        <TextInput
          label={strings.admin.users.emailLabel}
          placeholder={strings.admin.users.emailPlaceholder}
          required
          error={errors.email?.message}
          {...register('email')}
        />
        <Controller
          name="role"
          control={control}
          render={({ field }) => (
            <Select
              label={strings.admin.users.roleFieldLabel}
              required
              allowDeselect={false}
              data={ROLES.map((role) => ({ value: role, label: strings.roleLabel[role] }))}
              value={field.value}
              onChange={(value) => field.onChange(value ?? ROLES[0])}
            />
          )}
        />
        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={onCancel}>
            {strings.admin.users.cancel}
          </Button>
          <Button type="submit" loading={createUser.isPending}>
            {strings.admin.users.submit}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

export function CreateUserModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const [created, setCreated] = useState<CreatedUser | null>(null);

  function handleClose() {
    setCreated(null);
    onClose();
  }

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={created ? strings.admin.users.createdTitle : strings.admin.users.modalTitle}
      radius="md"
    >
      {created ? (
        <PasswordReveal created={created} onDone={handleClose} />
      ) : (
        <CreateUserForm onCreated={setCreated} onCancel={handleClose} />
      )}
    </Modal>
  );
}
