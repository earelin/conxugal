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

/**
 * The one-time password the create-user stub returns. It authenticates nothing
 * — it is the literal `users.json` serves, and the spec asserts the reveal
 * shows exactly it, so both copies have to match.
 */
// eslint-disable-next-line sonarjs/no-hardcoded-passwords -- a stub's canned response, not a credential
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

/**
 * The three runtime samples `metrics.json` streams, in order. Every figure the
 * metrics specs assert is derived from these by hand — heap 370/383/355 MB of
 * 1024, load 21/24/18 %, 48/50/47 threads — so a changed sample has to be
 * followed through to the expectations rather than silently weakening them.
 */
export const metricsSamples = [
  {
    timestamp: '2026-07-31T12:45:00Z',
    jvm: {
      heapUsedBytes: 387973120,
      heapMaxBytes: 1073741824,
      nonHeapUsedBytes: 134217728,
      threadCount: 48,
      uptimeMillis: 273120000,
      gcCollectionCount: 112,
      gcCollectionTimeMillis: 1840,
    },
    system: { cpuLoad: 0.21 },
    http: { requestCount: 18432, errorCount: 37 },
    datastorePool: { active: 3, idle: 7, max: 10 },
  },
  {
    timestamp: '2026-07-31T12:45:05Z',
    jvm: {
      heapUsedBytes: 401604608,
      heapMaxBytes: 1073741824,
      nonHeapUsedBytes: 134479872,
      threadCount: 50,
      uptimeMillis: 273125000,
      gcCollectionCount: 112,
      gcCollectionTimeMillis: 1840,
    },
    system: { cpuLoad: 0.24 },
    http: { requestCount: 18461, errorCount: 37 },
    datastorePool: { active: 4, idle: 6, max: 10 },
  },
  {
    timestamp: '2026-07-31T12:45:10Z',
    jvm: {
      heapUsedBytes: 372244480,
      heapMaxBytes: 1073741824,
      nonHeapUsedBytes: 134479872,
      threadCount: 47,
      uptimeMillis: 273130000,
      gcCollectionCount: 113,
      gcCollectionTimeMillis: 1902,
    },
    system: { cpuLoad: 0.18 },
    http: { requestCount: 18490, errorCount: 38 },
    datastorePool: { active: 2, idle: 8, max: 10 },
  },
];

/**
 * A sample whose error rate — 400 of 18 490, or 2.16 % — clears the 1 % boundary
 * the panel calls elevated. The healthy samples above all sit in the default
 * band, so without this one nothing distinguishes a working classification from
 * a badge hardcoded to `NORMAL`.
 */
export const elevatedErrorRateSample = {
  ...metricsSamples[2],
  http: { requestCount: 18490, errorCount: 400 },
};

/**
 * The values a leaking backend might smuggle into a sample. The real endpoint
 * never emits them (SPEC-0003 R21, asserted by the backend suite); they are here
 * so the panel can be proven to render only the fields it knows, rather than
 * echoing whatever the stream carries.
 */
export const secretsNeverStreamed = {
  jdbcUrl: 'jdbc:postgresql://db.interno.conxugal.gal:5432/conxugal',
  // eslint-disable-next-line sonarjs/no-hardcoded-passwords -- an invented value the panel must not render
  password: 'nunca-debe-aparecer-na-pantalla',
};

/** A sample carrying those values alongside the pool counts the panel does render. */
export const sampleWithSecrets = {
  ...metricsSamples[2],
  datastorePool: { ...metricsSamples[2].datastorePool, ...secretsNeverStreamed },
};

/** A `USER` session, for the scenario that checks the admin area stays hidden. */
export const nonAdminSession = {
  id: '7e91a3c5-2b68-4d17-9f83-1c4a5e6b8d20',
  email: accounts.neverLoggedIn.email,
  role: 'USER',
  createdAt: '2026-05-05T08:00:00Z',
  lastLoginAt: null,
};
