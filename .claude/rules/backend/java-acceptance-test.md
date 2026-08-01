---
paths:
  - "server/acceptance/**/test/java/**/*Test.java"
---

# Java acceptance tests

Black-box tests against a **real running instance of the packaged application**,
driven over the wire as a user would. No framework test runner boots the app — the
`acceptance` module has no compile dependency on the application's code and knows
only its base URL. Never use `@MicronautTest`, `@MockBean`, or any in-process
harness, and never reach inside to call a service or repository.

These sit at the tip of the pyramid: **deliberately few**, each covering a
high-value user scenario rather than a code path. Branches, boundaries, and error
paths belong in unit tests; a single external interaction belongs in an integration
test. Before adding a variation, check whether one of those already covers it.

The suite never starts the application itself, and its `test` task is disabled — it
runs under the `acceptance` task, which is not part of `check`/`build`. From
`server/`:

```
./gradlew :application:dockerBuild
docker compose --profile app up -d --wait
./gradlew :acceptance:installPlaywrightBrowsers   # first run / CI only
./gradlew acceptance -Dapp.baseUrl=http://localhost:8080
```

- **Take the base URL from `ApplicationUnderTest.BASE_URI`**, never a hard-coded host
  or port — that is what lets the same suite run against a compose stack or a
  deployed environment.
- **Drive the edge the scenario actually uses.** A UI journey goes through Playwright
  (real Chromium, the built assets); a machine-facing API scenario goes through
  REST-assured, formatted as a staircase per the backend code style rule. Don't drive
  the UI over raw HTTP when the point is what the user sees.
- **Mock downstream services as external stubs.** Calls to services we don't own
  (contratosdegalicia.gal, any third-party API) are served by a standalone WireMock
  process the running application is *configured* to point at. A run must never depend
  on a real remote being up. Assert recorded requests against the stub server when the
  interaction is part of the contract.
- **Pin all state.** It comes from what the `local` environment seeds (the
  `demo@local` / `demo` user and the `root@local` / `secret` administrator the journeys
  log in with). A scenario needing more must seed it from a version-controlled dataset
  against the instance's database and clean up afterwards — never through the
  application's own internals, and never depending on rows another test left behind.
  `ApplicationDatabase` reaches that database over JDBC; it defaults to the local
  compose stack, so a run against any other instance must pass `-Ddb.url`,
  `-Ddb.username` and `-Ddb.password` for *that* instance's datastore alongside
  `-Dapp.baseUrl`, or the run is refused.
- **Assert with AssertJ, and `PlaywrightAssertions` for the rendered page.** UI copy
  is Galician — assert the Galician string.
- **Name test methods in snake_case** telling the scenario — e.g.
  `unknown_api_route_returns_not_found_never_the_spa_shell`.
- **One user scenario per test**, structured Given (seeded state, stubbed downstreams,
  a logged-in browser context) / When / Then.
- **Browser lifecycle per class, context per test.** Launch Playwright and the browser
  in `@BeforeAll` and close them in `@AfterAll`; open a fresh `BrowserContext` in
  `@BeforeEach` and close it in `@AfterEach`, so no session or cookie leaks between
  tests.
