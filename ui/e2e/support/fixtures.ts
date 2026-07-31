/**
 * The accounts and values the default WireMock mappings serve.
 *
 * Kept in step with `ui/wiremock/mappings/` by hand — specs reference these
 * rather than repeating literals, so a fixture change lands in one place.
 */
export const accounts = {
  /** The only enabled ADMIN, so the UI blocks disabling it. */
  admin: { email: 'ana.pereira@conxugal.gal' },
  /** Enabled USER — the account the disable/enable journey toggles. */
  enabledUser: { id: '5c2d1e08-9a44-4f3b-8d21-7b6e0c9a1f52', email: 'brais.otero@conxugal.gal' },
  /** Enabled USER that has never logged in successfully. */
  neverLoggedIn: { email: 'diego.senra@conxugal.gal' },
  /** Disabled USER — still listed, and re-enablable. */
  disabledUser: { email: 'helena.mar@conxugal.gal' },
} as const;

/** The one-time password the create-user stub returns. */
export const generatedPassword = 'Tg7#kLp2Qw9$mZxR';

/** A `USER` session, for the scenario that checks the admin area stays hidden. */
export const nonAdminSession = {
  id: '7e91a3c5-2b68-4d17-9f83-1c4a5e6b8d20',
  email: accounts.neverLoggedIn.email,
  role: 'USER',
  createdAt: '2026-05-05T08:00:00Z',
  lastLoginAt: null,
};
