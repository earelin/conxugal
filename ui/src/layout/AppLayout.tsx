import {
  AppShell,
  Burger,
  Group,
  NavLink as MantineNavLink,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { NavLink, Outlet } from 'react-router';
import { navItems } from '../nav';
import { strings } from '../strings';

/**
 * Persistent application shell: a header with the product name and a navbar
 * carrying the primary navigation. The navbar collapses behind a burger on
 * narrow viewports so primary content never overflows horizontally.
 */
export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();

  return (
    <AppShell
      padding="md"
      header={{ height: 60 }}
      navbar={{ width: 260, breakpoint: 'sm', collapsed: { mobile: !opened } }}
    >
      <AppShell.Header>
        <Group h="100%" px="md" gap="sm" wrap="nowrap">
          <Burger
            opened={opened}
            onClick={toggle}
            hiddenFrom="sm"
            size="sm"
            aria-label="Alternar a navegación"
          />
          <Stack gap={0}>
            <Title order={1} size="h3" lh={1}>
              {strings.appName}
            </Title>
            <Text size="xs" c="dimmed" visibleFrom="sm">
              {strings.appTagline}
            </Text>
          </Stack>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="md">
        <nav aria-label="Navegación principal">
          {navItems.map((item) => (
            <MantineNavLink
              key={item.to}
              component={NavLink}
              to={item.to}
              end={item.to === '/'}
              label={item.label}
              onClick={close}
            />
          ))}
        </nav>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
