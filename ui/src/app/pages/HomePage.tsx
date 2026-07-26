import { Stack, Text, Title } from '@mantine/core';
import { strings } from '../../shared/lib/strings';

export function HomePage() {
  return (
    <Stack gap="sm">
      <Title order={2}>{strings.home.title}</Title>
      <Text c="dimmed" maw={640}>
        {strings.home.description}
      </Text>
    </Stack>
  );
}
