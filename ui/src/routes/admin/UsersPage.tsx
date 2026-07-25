import {
  ActionIcon,
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Group,
  Loader,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip,
  CopyButton,
} from '@mantine/core';
import {
  IconAlertCircle,
  IconAlertTriangle,
  IconCheck,
  IconCopy,
  IconPlus,
} from '@tabler/icons-react';
import { type FormEvent, useState } from 'react';
import { HttpError } from '../../api/httpClient';
import {
  type CreatedUser,
  type UserAccount,
  useCreateUser,
  useSetUserEnabled,
  useUsers,
} from '../../api/users';
import { strings } from '../../strings';

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('gl-ES', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function initialsOf(email: string): string {
  return email.split('@')[0].slice(0, 2).toUpperCase();
}

function isEmailValid(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function UsersError({ error }: { error: unknown }) {
  const message =
    error instanceof HttpError && error.status === 403
      ? strings.admin.users.errorForbidden
      : strings.admin.users.errorGeneric;

  return (
    <Alert color="red" icon={<IconAlertCircle size={18} />} title={strings.admin.users.errorTitle}>
      {message}
    </Alert>
  );
}

function PasswordReveal({ created, onDone }: { created: CreatedUser; onDone: () => void }) {
  return (
    <Stack gap="md">
      <Alert color="yellow" icon={<IconAlertTriangle size={18} />}>
        {strings.admin.users.passwordWarning}
      </Alert>
      <TextInput
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

interface CreateUserFormProps {
  onCreated: (created: CreatedUser) => void;
  onCancel: () => void;
}

function CreateUserForm({ onCreated, onCancel }: CreateUserFormProps) {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<'USER' | 'ADMIN'>('USER');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const createUser = useCreateUser();

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);

    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      setEmailError(strings.admin.users.emailRequired);
      return;
    }
    if (!isEmailValid(trimmedEmail)) {
      setEmailError(strings.admin.users.emailInvalid);
      return;
    }
    setEmailError(null);

    createUser.mutate(
      { email: trimmedEmail, role },
      {
        onSuccess: onCreated,
        onError: (error) => {
          if (error instanceof HttpError && error.status === 409) {
            setEmailError(strings.admin.users.duplicateEmailError);
          } else {
            setFormError(strings.admin.users.createGenericError);
          }
        },
      },
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <Stack gap="md">
        {formError && <Alert color="red">{formError}</Alert>}
        <TextInput
          label={strings.admin.users.emailLabel}
          placeholder={strings.admin.users.emailPlaceholder}
          required
          value={email}
          onChange={(event) => setEmail(event.currentTarget.value)}
          error={emailError}
        />
        <Select
          label={strings.admin.users.roleFieldLabel}
          required
          allowDeselect={false}
          data={[
            { value: 'USER', label: strings.roleLabel.USER },
            { value: 'ADMIN', label: strings.roleLabel.ADMIN },
          ]}
          value={role}
          onChange={(value) => setRole(value === 'ADMIN' ? 'ADMIN' : 'USER')}
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

function CreateUserModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
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

interface UserRowProps {
  user: UserAccount;
  isLastEnabledAdmin: boolean;
  isMutating: boolean;
  onToggle: (user: UserAccount) => void;
}

function UserRow({ user, isLastEnabledAdmin, isMutating, onToggle }: UserRowProps) {
  const toggleButton = (
    <Button
      size="xs"
      variant={user.enabled ? 'default' : 'light'}
      color={user.enabled ? undefined : 'green'}
      loading={isMutating}
      aria-disabled={isLastEnabledAdmin}
      style={isLastEnabledAdmin ? { opacity: 0.5, cursor: 'not-allowed' } : undefined}
      onClick={() => {
        if (isLastEnabledAdmin) {
          return;
        }
        onToggle(user);
      }}
    >
      {user.enabled ? strings.admin.users.disable : strings.admin.users.enable}
    </Button>
  );

  return (
    <Table.Tr style={{ opacity: user.enabled ? 1 : 0.6 }}>
      <Table.Td>
        <Group gap="sm" wrap="nowrap">
          <Avatar radius="xl" color={user.enabled ? 'indigo' : 'gray'}>
            {initialsOf(user.email)}
          </Avatar>
          <Text>{user.email}</Text>
        </Group>
      </Table.Td>
      <Table.Td>
        <Badge variant="light" color={user.role === 'ADMIN' ? 'indigo' : 'gray'}>
          {strings.roleLabel[user.role]}
        </Badge>
      </Table.Td>
      <Table.Td>
        <Badge variant="light" color={user.enabled ? 'green' : 'gray'}>
          {user.enabled ? strings.admin.users.stateEnabled : strings.admin.users.stateDisabled}
        </Badge>
      </Table.Td>
      <Table.Td>{formatDate(user.createdAt)}</Table.Td>
      <Table.Td>
        {user.lastLoginAt ? (
          formatDate(user.lastLoginAt)
        ) : (
          <Text fs="italic" c="dimmed">
            {strings.admin.users.never}
          </Text>
        )}
      </Table.Td>
      <Table.Td ta="right">
        {isLastEnabledAdmin ? (
          <Tooltip label={strings.admin.users.lastAdminTooltip}>{toggleButton}</Tooltip>
        ) : (
          toggleButton
        )}
      </Table.Td>
    </Table.Tr>
  );
}

function UsersTable({ users }: { users: UserAccount[] }) {
  const setEnabled = useSetUserEnabled();
  const [actionError, setActionError] = useState<string | null>(null);
  const enabledAdminCount = users.filter((user) => user.role === 'ADMIN' && user.enabled).length;

  function toggleEnabled(user: UserAccount) {
    setActionError(null);
    setEnabled.mutate(
      { id: user.id, enabled: !user.enabled },
      {
        onError: (error) => {
          setActionError(
            error instanceof HttpError && error.status === 409
              ? strings.admin.users.lastAdminError
              : strings.admin.users.toggleGenericError,
          );
        },
      },
    );
  }

  return (
    <Stack gap="sm">
      {actionError && (
        <Alert color="red" icon={<IconAlertCircle size={18} />}>
          {actionError}
        </Alert>
      )}
      <Card withBorder radius="md" padding={0}>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{strings.admin.users.columnPerson}</Table.Th>
              <Table.Th>{strings.admin.users.columnRole}</Table.Th>
              <Table.Th>{strings.admin.users.columnState}</Table.Th>
              <Table.Th>{strings.admin.users.columnCreated}</Table.Th>
              <Table.Th>{strings.admin.users.columnLastLogin}</Table.Th>
              <Table.Th ta="right">{strings.admin.users.columnActions}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {users.map((user) => (
              <UserRow
                key={user.id}
                user={user}
                isLastEnabledAdmin={user.role === 'ADMIN' && user.enabled && enabledAdminCount <= 1}
                isMutating={setEnabled.isPending && setEnabled.variables?.id === user.id}
                onToggle={toggleEnabled}
              />
            ))}
          </Table.Tbody>
        </Table>
      </Card>
      <Text size="xs" c="dimmed">
        {strings.admin.users.neverDeletedNote}
      </Text>
    </Stack>
  );
}

export function UsersPage() {
  const { data, isPending, isError, error } = useUsers();
  const [modalOpened, setModalOpened] = useState(false);

  return (
    <Stack gap="md">
      <Group justify="space-between" align="flex-end">
        <Stack gap={0}>
          <Title order={2}>{strings.admin.users.title}</Title>
          <Text c="dimmed">{strings.admin.users.subtitle}</Text>
        </Stack>
        <Button
          leftSection={<IconPlus size={16} />}
          disabled={!data}
          onClick={() => setModalOpened(true)}
        >
          {strings.admin.users.createButton}
        </Button>
      </Group>

      {isPending && <Loader />}
      {isError && !data && <UsersError error={error} />}
      {data && <UsersTable users={data} />}

      <CreateUserModal opened={modalOpened} onClose={() => setModalOpened(false)} />
    </Stack>
  );
}
