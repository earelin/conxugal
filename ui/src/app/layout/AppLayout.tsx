import {
  AppShell,
  Burger,
  Divider,
  Group,
  NavLink as MantineNavLink,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { NavLink, Outlet } from 'react-router';

import { useCurrentUser } from '../../shared/entities/currentUser';
import { useVisibleOrganos } from '../../shared/entities/organos';
import { strings } from '../../shared/lib/strings';
import { OrganoPicker } from '../../shared/ui/OrganoPicker';
import { visibleNavSections } from '../nav';
import { UserMenu } from './UserMenu';

/**
 * Persistent application shell: a header with the product name and a navbar
 * carrying the primary navigation. The navbar collapses behind a burger on
 * narrow viewports so primary content never overflows horizontally.
 */
export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();
  const { data: currentUser } = useCurrentUser();
  const sections = visibleNavSections(currentUser?.role);
  // One condition gates the read and the render alike: a disabled query reports
  // `isPending` for as long as it stays off, so a picker mounted before the
  // session resolves would sit on a spinner that never ends.
  const organos = useVisibleOrganos({ enabled: currentUser !== undefined });

  return (
    <AppShell
      padding="md"
      header={{ height: 60 }}
      navbar={{ width: 260, breakpoint: 'sm', collapsed: { mobile: !opened } }}
    >
      <AppShell.Header>
        <Group h="100%" px="md" gap="sm" wrap="nowrap" justify="space-between">
          <Group gap="sm" wrap="nowrap">
            <Burger
              opened={opened}
              onClick={toggle}
              hiddenFrom="sm"
              size="sm"
              aria-label="Alternar a navegación"
            />
            <img src="/logo.svg" alt="" width={36} height={36} />
            <Stack gap={0}>
              <Title order={1} size="h3" lh={1}>
                {strings.appName}
              </Title>
              <Text size="xs" c="dimmed" visibleFrom="sm">
                {strings.appTagline}
              </Text>
            </Stack>
          </Group>

          {currentUser && <UserMenu currentUser={currentUser} />}
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="md">
        {/* Not a nav item and not a link: the picker is chrome that opens an
            Órgano's contracts, so it sits above the navigation landmark rather
            than inside it. */}
        {currentUser && (
          <>
            <OrganoPicker
              view={organos.view}
              isPending={organos.isPending}
              isFetching={organos.isFetching}
              isError={organos.isError}
              onRetry={organos.refetch}
              onNavigate={close}
            />
            <Divider my="md" />
          </>
        )}

        <nav aria-label="Navegación principal">
          <Stack gap="lg">
            {sections.map((section) => (
              // Only the ungrouped primary section has no label to key on.
              <Stack gap={2} key={section.label ?? 'primary'}>
                {section.label && (
                  <Text size="xs" fw={700} c="dimmed" tt="uppercase" px="xs">
                    {section.label}
                  </Text>
                )}
                {section.items.map((item) => (
                  <MantineNavLink
                    key={item.to}
                    component={NavLink}
                    to={item.to}
                    end={item.end}
                    label={item.label}
                    leftSection={item.icon ? <item.icon size={18} /> : undefined}
                    onClick={close}
                  />
                ))}
              </Stack>
            ))}
          </Stack>
        </nav>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
