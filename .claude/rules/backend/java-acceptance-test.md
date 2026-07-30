---
paths:
  - "server/acceptance/**/test/java/**/*Test.java"
---

# Java acceptance tests

Black-box tests against a **real running instance of the packaged application**, driven
over the wire as a user would: a browser journey with **Playwright**, or an HTTP call
with **REST-assured**. No framework test runner boots the app — the `acceptance` module
has no compile dependency on the application's code and knows only its base URL. Assert
with **AssertJ** (and `PlaywrightAssertions` for what the page shows), name test methods
in **snake_case**.

These sit at the tip of the pyramid: **deliberately few**, each earning its place by
covering a high-value user scenario, not a code path.

## Scope

- **A real running instance, treated as a black box.** The app runs as its own
  container — the image built by `:application:dockerBuild`, started by
  `docker-compose.yml`'s `app` profile against a real PostgreSQL — before the suite
  connects. Never use `@MicronautTest`, `@MockBean`, or any in-process harness, and
  never reach inside to call a service or repository; the test knows the public contract
  and a base URL.
- **A high-value user scenario, end to end** — a story a stakeholder cares about, told
  through the running system. Branches, boundaries, and error paths belong in unit tests
  under `src/test/java`; a single external interaction belongs in an integration test
  under `src/integrationTest/java`. Do not re-test those here.
- **Deliberately few.** Before adding a variation, ask whether a unit or integration
  test already covers it — it usually does.

## Running the suite

The suite never starts the application itself, and its `test` task is disabled — it runs
under the `acceptance` task, which is not part of `check`/`build`. From `server/`:

```
./gradlew :application:dockerBuild
docker compose --profile app up -d --wait
./gradlew :acceptance:installPlaywrightBrowsers   # first run / CI only
./gradlew acceptance -Dapp.baseUrl=http://localhost:8080
```

## Rules

- **Take the base URL from `ApplicationUnderTest.BASE_URI`**, never a hard-coded host or
  port. It reads the `app.baseUrl` system property and falls back to
  `http://localhost:8080` for a local run, which is what lets the same suite run against
  a compose stack or a deployed environment.
- **Pick the outer edge the scenario actually uses.** A user journey through the UI is
  driven with Playwright — real Chromium, the built assets, `page.navigate(...)` and
  locators; a machine-facing API scenario is driven with REST-assured
  (`given()...when().get(...).then().statusCode(...)`), formatted as a staircase per the
  backend code style rule. Do not drive the UI through raw HTTP when the point is what
  the user sees.
- **Mock every downstream service as an external stub.** Outbound calls to services we
  do not own (contratosdegalicia.gal, any third-party API) are served by a standalone
  WireMock process the running application is *configured* to point at — not an
  in-process mock. A run must never depend on a real remote being up. Assert recorded
  requests against the stub server when the interaction is part of the contract.
- **Pin all state.** State comes from the fixed data the `local` environment seeds (the
  `demo@local` / `demo` user the journeys log in with). A scenario needing more must seed
  it from a version-controlled dataset against the running instance's database and clean
  it afterwards — never by calling the application's own internals, and never depending
  on rows left by another test.
- **Assert with AssertJ, and with `PlaywrightAssertions` for the rendered page.** Use
  `assertThat(response.status())`, `assertThat(...).contains(...)` for values read back,
  and `PlaywrightAssertions.assertThat(page.getByText("..."))` for what the user sees.
  UI copy is Galician — assert the Galician string.
- **Test method names are snake_case** telling the scenario — e.g.
  `known_client_side_route_falls_back_to_the_spa_shell`,
  `unknown_api_route_returns_not_found_never_the_spa_shell`. No `test` prefix. Add
  `@DisplayName` only when a full sentence adds value beyond the name.
- **One user scenario per test**, structured Given (seeded state, stubbed downstreams,
  a logged-in browser context) / When (drive the instance over the wire) / Then (assert
  the user-visible outcome, and the recorded downstream interaction where it matters).
- **Keep the browser lifecycle per class and the context per test.** Launch Playwright
  and the browser in `@BeforeAll`, close them in `@AfterAll`; open a fresh
  `BrowserContext` in `@BeforeEach` and close it in `@AfterEach`, so no session or
  cookie leaks between tests.

## Format

A browser journey against the running instance:

```java
class AuthenticatedSpaRoutingTest {

  private static final String ROOT_URL = ApplicationUnderTest.BASE_URI + "/";
  private static final String LOGIN_URL = ApplicationUnderTest.BASE_URI + "/login";

  private static Playwright playwright;
  private static Browser browser;

  private BrowserContext context;
  private Page page;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch();
  }

  @AfterAll
  static void closeBrowser() {
    browser.close();
    playwright.close();
  }

  @BeforeEach
  void logInAsDemoUser() {
    context = browser.newContext();
    page = context.newPage();
    page.navigate(LOGIN_URL);
    page.locator("#username").fill("demo@local");
    page.locator("#password").fill("demo");
    page.locator("button[type=submit]").click();
    page.waitForURL(ROOT_URL);
  }

  @AfterEach
  void closeContext() {
    context.close();
  }

  @Test
  void known_client_side_route_falls_back_to_the_spa_shell() {
    Response response = page.navigate(ApplicationUnderTest.BASE_URI + "/acerca");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.headerValue("content-type")).contains("text/html");
    PlaywrightAssertions.assertThat(page.getByText("Acerca do proxecto")).isVisible();
  }
}
```

An API scenario over the wire, with REST-assured pointed at the same instance:

```java
class CurrentUserAcceptanceTest {

  @BeforeAll
  static void pointAtTheRunningInstance() {
    RestAssured.baseURI = ApplicationUnderTest.BASE_URI;
  }

  @Test
  void unauthenticated_caller_is_unauthorized() {
    when()
        .get("/api/me")
    .then()
        .statusCode(401);
  }
}
```

## Steps

1. Choose one high-value user scenario and, from it, enumerate the downstream services
   to stub and the state to pin.
2. Stand up the stubs and start the application configured to point at them
   (`:application:dockerBuild`, then the compose `app` profile); the run passes the
   instance's base URL in as `-Dapp.baseUrl`.
3. Write a snake_case `@Test` per scenario: given the seeded state and stubbed
   downstreams, when you drive the instance with Playwright or REST-assured, then assert
   the user-visible outcome with AssertJ.
4. Clean up whatever the scenario seeded, and close the browser context, so the class
   stays order-independent.
5. Run `./gradlew acceptance -Dapp.baseUrl=...` against the running stack and summarise
   the scenarios covered.
