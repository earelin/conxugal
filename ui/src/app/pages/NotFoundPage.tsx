import { Button, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router';

import { strings } from '../../shared/lib/strings';

/** In-shell not-found state, shown for unmatched routes. */
export function NotFoundPage() {
  return (
    <Stack align="flex-start" gap="xs">
      <Text fw={700} fz={48} lh={1} c="indigo">
        {strings.notFound.code}
      </Text>
      <Title order={2}>{strings.notFound.title}</Title>
      <Text c="dimmed">{strings.notFound.description}</Text>
      <Button component={Link} to="/" mt="sm">
        {strings.notFound.back}
      </Button>
    </Stack>
  );
}
