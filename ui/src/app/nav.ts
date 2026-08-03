import { type Icon, IconLayoutDashboard, IconSitemap, IconUsers } from '@tabler/icons-react';

import type { Role } from '../shared/entities/currentUser';
import { strings } from '../shared/lib/strings';

export interface NavItem {
  /** Visible label (Galician). */
  label: string;
  to: string;
  /** Whether the link should only match its exact path, not descendant routes. */
  end?: boolean;
  icon?: Icon;
}

export interface NavSection {
  /** Uppercase section label (Galician); omitted for the ungrouped primary section. */
  label?: string;
  items: NavItem[];
  /** Roles the section is shown to; omitted means shown to everyone. */
  visibleTo?: Role[];
}

/** Navigation, rendered in the AppShell navbar. */
export const navSections: NavSection[] = [
  {
    items: [
      { label: strings.nav.home, to: '/', end: true },
      { label: strings.nav.about, to: '/acerca' },
    ],
  },
  {
    label: strings.nav.adminSection,
    visibleTo: ['ADMIN'],
    items: [
      { label: strings.nav.panel, to: '/administracion', end: true, icon: IconLayoutDashboard },
      { label: strings.nav.users, to: '/administracion/usuarios', icon: IconUsers },
      { label: strings.nav.organos, to: '/administracion/organos', icon: IconSitemap },
    ],
  },
];

/**
 * Sections visible to the given role. `role` is `undefined` while the
 * session is still loading or failed to resolve, in which case role-gated
 * sections stay hidden rather than flashing on and off.
 */
export function visibleNavSections(role: Role | undefined): NavSection[] {
  return navSections.filter(
    (section) => !section.visibleTo || (role !== undefined && section.visibleTo.includes(role)),
  );
}
