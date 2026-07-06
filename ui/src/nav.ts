import { strings } from './strings';

export interface NavItem {
  /** Visible label (Galician). */
  label: string;
  to: string;
}

/** Primary navigation, rendered in the AppShell navbar (SPEC-001 R2). */
export const navItems: NavItem[] = [
  { label: strings.nav.home, to: '/' },
  { label: strings.nav.about, to: '/acerca' },
];
