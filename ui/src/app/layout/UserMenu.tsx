import { Avatar, Box, Group, Loader, Menu, Stack, Text, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconChevronDown, IconLogout } from '@tabler/icons-react';

import type { CurrentUser } from '../../shared/entities/currentUser';
import { useLogout } from '../../shared/entities/currentUser';
import { HttpError } from '../../shared/lib/httpClient';
import { strings } from '../../shared/lib/strings';
import { initialsOf } from '../../shared/ui/avatar';
import { ErrorAlert } from '../../shared/ui/ErrorAlert';
import classes from './UserMenu.module.css';

export function UserMenu({ currentUser }: { currentUser: CurrentUser }) {
  const [opened, { set: setOpened }] = useDisclosure(false);
  const logout = useLogout();

  function handleOpenedChange(value: boolean) {
    setOpened(value);
    if (!value) logout.reset();
  }

  // A 401 means the session was already gone; the shared session-loss handler
  // (queryClient.ts) redirects for that case, so this alert is only for a
  // failure that isn't a lost session.
  const showFailure =
    logout.isError && !(logout.error instanceof HttpError && logout.error.status === 401);

  return (
    <Menu
      opened={opened}
      onChange={handleOpenedChange}
      position="bottom-end"
      offset={8}
      shadow="md"
    >
      <Menu.Target>
        <UnstyledButton
          type="button"
          aria-label={strings.userMenu.trigger}
          className={classes.trigger}
        >
          <Group gap="sm" wrap="nowrap">
            <Stack gap={0} align="flex-end" visibleFrom="sm">
              <Text size="sm">{currentUser.email}</Text>
              <Text size="xs" c="dimmed">
                {strings.roleLabel[currentUser.role]}
              </Text>
            </Stack>
            <Avatar radius="xl" color="indigo">
              {initialsOf(currentUser.email)}
            </Avatar>
            <Box visibleFrom="sm">
              <IconChevronDown
                size={16}
                stroke={1.8}
                style={{
                  transform: opened ? 'rotate(180deg)' : undefined,
                  transition: 'transform 150ms ease',
                }}
              />
            </Box>
          </Group>
        </UnstyledButton>
      </Menu.Target>

      <Menu.Dropdown>
        <Stack gap={2} px="sm" py={6} role="presentation">
          <Text size="sm" fw={500}>
            {currentUser.email}
          </Text>
          <Text size="xs" c="dimmed">
            {strings.roleLabel[currentUser.role]}
          </Text>
        </Stack>
        <Menu.Divider />
        <Menu.Item
          leftSection={<IconLogout size={16} stroke={1.8} />}
          rightSection={logout.isPending ? <Loader size={14} /> : undefined}
          data-disabled={logout.isPending || undefined}
          aria-disabled={logout.isPending || undefined}
          closeMenuOnClick={false}
          onClick={() => {
            if (logout.isPending) return;
            logout.mutate();
          }}
        >
          {strings.userMenu.logout}
        </Menu.Item>
        {showFailure && (
          <Box role="presentation" mx="sm" mt="xs" mb={6}>
            <ErrorAlert>{strings.userMenu.logoutError}</ErrorAlert>
          </Box>
        )}
      </Menu.Dropdown>
    </Menu>
  );
}
