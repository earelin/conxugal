---
paths:
  - "server/**/src/integrationTest/java/**/*Test.java"
---

# Java integration tests

Tests that verify how the code interacts with an **external process**: the database,
an outbound HTTP client, or the application's own controllers reached over HTTP.
Real dependencies run in **Testcontainers**, assertions use **AssertJ** (and
**AssertJ DB** for database state), endpoints are driven with **REST-assured**, and
test methods are named in **snake_case**.

Only `application` and `infrastructure` have an `integrationTest` suite, and neither
runs as part of `check`/`build` — run `./gradlew :application:integrationTest` or
`./gradlew :infrastructure:integrationTest` from `server/` explicitly.
`infrastructure`'s suite needs a Docker daemon; `application`'s only needs a JVM.

## Scope

- **Only interactions that cross a process boundary.** A test earns the integration
  label when it exercises a real external dependency: the database, an outbound HTTP
  client hitting a stubbed remote, or a `@Controller` as reached over HTTP.
- **Not business logic in isolation** — that belongs in a unit test under
  `src/test/java`. Do not re-test pure logic here; test that the wiring to the outside
  world behaves.
- **Pick the narrowest boundary that answers the question.** A repository test needs a
  real database but not the running application (`@MicronautTest(startApplication =
  false)`); a controller test needs the embedded server but its domain collaborators
  mocked.

## Rules

- **Live in the integration source set.** Never place these under `src/test` next to
  unit tests — they belong in the module's `src/integrationTest/java`, so they run
  apart from the fast unit suite.
- **Real dependencies run in Testcontainers.** Start PostgreSQL, WireMock, and any
  other backing service as a container (`@Testcontainers(disabledWithoutDocker = true)`
  with a `static` `@Container` field), and feed its coordinates to the context by
  implementing `TestPropertyProvider` — starting the container in `getProperties()` if
  it isn't running yet, since that runs before the JUnit extension. Never hit a shared
  or developer-local instance; each run must be hermetic and disposable.
- **Assert with AssertJ; assert database state with AssertJ DB.** Use
  `assertThat(...)` for in-memory values, and AssertJ DB
  (`AssertDbConnectionFactory.of(dataSource).create().table("...").build()`, then
  `assertThat(table).row(0).value("column")...`) to assert rows written to the database
  rather than round-tripping through the code under test.
- **Test controllers with the embedded server and mocked collaborators.** Use
  `@MicronautTest`, replace the controller's domain collaborators with
  `@MockBean(Type.class)` factory methods returning `mock(Type.class)` (so only the HTTP
  layer — routing, serialization, status codes, validation, security — is under test),
  and drive endpoints with REST-assured through the injected `RequestSpecification spec`
  test parameter. Format `given()/when()/then()` as a staircase, per the backend code
  style rule.
- **Mock with strict stubbing; never lenient, never `verify(...)`.** Stub only what the
  endpoint needs with `when(...).thenReturn(...)` / `.thenThrow(...)` and assert the HTTP
  response. Do not relax strictness with `lenient()` or `Strictness.LENIENT`, and do not
  add `verify(...)` — a strict stub already fails the test if the collaborator is never
  called, and the status and body are what the test asserts on.
- **Test method names are snake_case** describing the interaction — e.g.
  `persists_organo_and_assigns_generated_id`, `returns_404_when_organo_is_unknown`,
  `unauthenticated_caller_is_unauthorized`. No `test` prefix.
- **Keep tests deterministic and isolated.** One container lifecycle per class
  (`@TestInstance(PER_CLASS)`), state cleaned per test — truncate the tables the test
  touched in `@AfterEach`, reset WireMock stubs in `@BeforeEach`. Never depend on
  execution order or leftover data.

## Format

Repository against a real PostgreSQL (Testcontainers + AssertJ DB):

```java
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcOrganoRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  @Override
  public @NonNull Map<String, String> getProperties() {
    if (!postgres.isRunning()) {
      postgres.start();
    }
    return Map.of(
        "datasources.default.url", postgres.getJdbcUrl(),
        "datasources.default.username", postgres.getUsername(),
        "datasources.default.password", postgres.getPassword(),
        "datasources.default.driverClassName", postgres.getDriverClassName(),
        "datasources.default.dialect", "POSTGRES",
        "flyway.datasources.default.enabled", "true"
    );
  }

  @Inject
  OrganoRepository organoRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE organo_contratacion");
    }
  }

  @Test
  void inserts_an_organo_with_database_generated_id() {
    organoRepository.insert(new OrganoDeContratacion("consorcio-x", "Consorcio X"));

    Table organos =
        AssertDbConnectionFactory.of(dataSource).create().table("organo_contratacion").build();
    assertThat(organos).hasNumberOfRows(1);
    assertThat(organos).row(0)
        .value("source_key").isEqualTo("consorcio-x")
        .value("active").isTrue();
  }
}
```

Controller over HTTP (embedded server up, domain collaborator mocked, REST-assured):

```java
@MicronautTest
class ImportOrganosControllerIntegrationTest extends AuthenticationTestSupport {

  @Inject
  ImportOrganos importOrganos;

  @MockBean(ImportOrganos.class)
  ImportOrganos importOrganosMock() {
    return mock(ImportOrganos.class);
  }

  @Test
  void source_failure_is_reported_as_server_error(RequestSpecification spec) {
    when(importOrganos.run()).thenReturn(ImportOutcome.failure());
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .post("/api/admin/organos/import")
    .then()
        .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
  }
}
```

## Steps

1. Identify the external process under test and pick the narrowest boundary — a
   repository plus the database, a client plus a WireMock remote, or a controller plus
   the embedded server with its collaborators mocked.
2. Stand the dependency up in a container and hand its coordinates to the context via
   `TestPropertyProvider`; for a controller, start the application and mock the
   collaborators with `@MockBean`.
3. Write snake_case `@Test`s: exercise the interaction, then assert with AssertJ —
   AssertJ DB for database state, REST-assured expectations for HTTP responses.
4. Clean the state the test wrote (`@AfterEach` truncate, WireMock reset) so the class
   stays order-independent.
5. Run the module's suite (`./gradlew :application:integrationTest` or
   `:infrastructure:integrationTest` from `server/`) and summarise the interactions
   covered.
