import { type Page } from '@playwright/test';

/** The app shell's persistent navigation, which every page renders. */
export function nav(page: Page) {
  return page.getByRole('navigation', { name: 'Navegación principal' });
}

export function navLink(page: Page, name: string) {
  return nav(page).getByRole('link', { name });
}
