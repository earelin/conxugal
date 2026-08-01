import { Button, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
import { useState } from 'react';

import { isHttpStatus } from '../../../shared/lib/httpError';
import { strings } from '../../../shared/lib/strings';
import { ErrorAlert } from '../../../shared/ui/ErrorAlert';
import { CreateUserModal } from './CreateUserModal';
import { useUsers } from './users';
import { UsersTable } from './UsersTable';

function UsersError({ error }: { error: unknown }) {
  const message = isHttpStatus(error, 403)
    ? strings.admin.users.errorForbidden
    : strings.admin.users.errorGeneric;

  return <ErrorAlert title={strings.admin.users.errorTitle}>{message}</ErrorAlert>;
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
