import { Stack, Text, Title } from '@mantine/core';

import { strings } from '../../shared/lib/strings';

export function AboutPage() {
  return (
    <Stack gap="sm">
      <Title order={2}>{strings.about.title}</Title>
      <Text c="dimmed" maw={640}>
        {strings.about.description}
      </Text>
    </Stack>
  );
}
