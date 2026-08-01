/**
 * The accounts and values the default WireMock mappings serve.
 *
 * Kept in step with `ui/wiremock/mappings/` by hand — specs reference these
 * rather than repeating literals, so a fixture change lands in one place.
 */
export const accounts = {
  /**
   * The only enabled ADMIN. That is the state in which the UI blocks the
   * disable control — visible in dev, but no spec asserts it (see the known
   * gaps in `wiremock/README.md`).
   */
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

/**
 * The status the default mapping serves, but with the datastore down — for the
 * scenario that checks a degraded backend is not masked by a cached snapshot.
 * Later `checkedAt` and `uptimeMillis` than the healthy one, as a real later
 * poll would report.
 */
export const degradedSystemStatus = {
  status: 'DEGRADED',
  datastore: { reachable: false },
  checkedAt: '2026-07-31T12:50:00Z',
  application: { version: '0.1.0-SNAPSHOT', environment: 'local' },
  runtime: {
    javaVersion: '25.0.1',
    javaVendor: 'Eclipse Adoptium',
    uptimeMillis: 273420000,
    memoryUsedBytes: 536870912,
    memoryMaxBytes: 1073741824,
    osName: 'Linux',
    osArch: 'amd64',
  },
};

/** A `USER` session, for the scenario that checks the admin area stays hidden. */
export const nonAdminSession = {
  id: '7e91a3c5-2b68-4d17-9f83-1c4a5e6b8d20',
  email: accounts.neverLoggedIn.email,
  role: 'USER',
  createdAt: '2026-05-05T08:00:00Z',
  lastLoginAt: null,
};
