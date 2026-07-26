import { Avatar, Badge, Button, Card, Group, Stack, Table, Text, Tooltip } from '@mantine/core';
import { useState } from 'react';
import { type UserAccount, useSetUserEnabled } from './users';
import { initialsOf } from '../../shared/ui/avatar';
import { formatDate } from '../../shared/lib/date';
import { isHttpStatus } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';

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

  const dimmedCellStyle = user.enabled ? undefined : { opacity: 0.6 };

  return (
    <Table.Tr>
      <Table.Td style={dimmedCellStyle}>
        <Group gap="sm" wrap="nowrap">
          <Avatar radius="xl" color={user.enabled ? 'indigo' : 'gray'}>
            {initialsOf(user.email)}
          </Avatar>
          <Text>{user.email}</Text>
        </Group>
      </Table.Td>
      <Table.Td style={dimmedCellStyle}>
        <Badge variant="light" color={user.role === 'ADMIN' ? 'indigo' : 'gray'}>
          {strings.roleLabel[user.role]}
        </Badge>
      </Table.Td>
      <Table.Td style={dimmedCellStyle}>
        <Badge variant="light" color={user.enabled ? 'green' : 'gray'}>
          {user.enabled ? strings.admin.users.stateEnabled : strings.admin.users.stateDisabled}
        </Badge>
      </Table.Td>
      <Table.Td style={dimmedCellStyle}>{formatDate(user.createdAt)}</Table.Td>
      <Table.Td style={dimmedCellStyle}>
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

export function UsersTable({ users }: { users: UserAccount[] }) {
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
            isHttpStatus(error, 409)
              ? strings.admin.users.lastAdminError
              : strings.admin.users.toggleGenericError,
          );
        },
      },
    );
  }

  return (
    <Stack gap="sm">
      {actionError && <ErrorAlert>{actionError}</ErrorAlert>}
      <Card withBorder radius="md" padding={0}>
        <Table aria-label={strings.admin.users.title}>
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
