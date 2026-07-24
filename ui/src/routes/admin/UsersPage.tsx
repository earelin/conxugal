import { Stack, Text, Title } from '@mantine/core';
import { strings } from '../../strings';

export function UsersPage() {
  return (
    <Stack gap="md">
      <Stack gap={0}>
        <Title order={2}>{strings.admin.users.title}</Title>
        <Text c="dimmed">{strings.admin.users.subtitle}</Text>
      </Stack>
      <Text c="dimmed">{strings.admin.users.placeholder}</Text>
    </Stack>
  );
}
