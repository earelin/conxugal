import { Stack, Text } from '@mantine/core';

interface DialogContextBlockProps {
  label: string;
  name: string;
  /** Extra context under the name, where the dialog has any. */
  note?: string;
}

/** The half of a dialog's pair that is already decided, stated and not editable. */
export function DialogContextBlock({ label, name, note }: DialogContextBlockProps) {
  return (
    <Stack gap={2}>
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Text fw={600}>{name}</Text>
      {note && (
        <Text size="xs" c="dimmed">
          {note}
        </Text>
      )}
    </Stack>
  );
}
