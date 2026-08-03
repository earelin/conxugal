import { type Page } from '@playwright/test';

/** The app shell's persistent navigation, which every page renders. */
export function nav(page: Page) {
  return page.getByRole('navigation', { name: 'Navegación principal' });
}

export function navLink(page: Page, name: string) {
  return nav(page).getByRole('link', { name });
}

/** A row of the user-administration table, found by the account it lists. */
export function rowFor(page: Page, email: string) {
  return page.getByRole('row').filter({ hasText: email });
}

// The enabled labels are substrings of the disabled ones — "Activada" of
// "Desactivada", "Activar" of "Desactivar" — and both getByText and the `name`
// option match on substrings, case-insensitively. Every assertion on this pair
// must therefore be exact, or a disabled account would satisfy it.
export function enabledBadge(row: ReturnType<typeof rowFor>) {
  return row.getByText('Activada', { exact: true });
}

export function toggleButton(row: ReturnType<typeof rowFor>, label: 'Activar' | 'Desactivar') {
  return row.getByRole('button', { name: label, exact: true });
}
