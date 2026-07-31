---
paths:
  - "server/**/src/integrationTest/java/**/*Test.java"
---

# Java integration tests

Tests for interactions that cross a **process boundary**: a repository against the
database, an outbound HTTP client against a stubbed remote, or a `@Controller`
reached over HTTP. Business logic in isolation belongs in a unit test under
`src/test/java` — don't re-test it here. Pick the narrowest boundary that answers the
question: a repository test needs a real database but not the running application
(`@MicronautTest(startApplication = false)`); a controller test needs the embedded
server with its domain collaborators mocked.

Only `application` and `infrastructure` have an `integrationTest` suite, and neither
runs as part of `check`/`build` — run `./gradlew :application:integrationTest` or
`:infrastructure:integrationTest` from `server/` explicitly. `infrastructure`'s suite
needs a Docker daemon; `application`'s only needs a JVM.

- **Real dependencies run in Testcontainers**, never a shared or developer-local
  instance. Declare a `static` `@Container` under
  `@Testcontainers(disabledWithoutDocker = true)` and hand its coordinates to the
  context by implementing `TestPropertyProvider` — starting the container inside
  `getProperties()` if it isn't running yet, since that runs before the JUnit
  extension.
- **Assert database state with AssertJ DB**, not by round-tripping through the code
  under test: `AssertDbConnectionFactory.of(dataSource).create().table("...").build()`,
  then `assertThat(table).row(0).value("column")...`. Everything else is AssertJ.
- **Test controllers with the embedded server and mocked collaborators.** Under
  `@MicronautTest`, replace domain collaborators with `@MockBean(Type.class)` factory
  methods returning `mock(Type.class)`, so only the HTTP layer — routing,
  serialization, status codes, validation, security — is under test. Drive endpoints
  with REST-assured through the injected `RequestSpecification spec` test parameter,
  formatted as a staircase per the backend code style rule.
- **Stub only what the endpoint needs, and never `verify(...)`** — assert the HTTP
  response instead. The exception is asserting that an interaction did *not* happen
  (`never()`, `verifyNoInteractions`), which stubbing cannot express.
- **Name test methods in snake_case**, describing the interaction — e.g.
  `persists_organo_and_assigns_generated_id`, `returns_404_when_organo_is_unknown`.
- **Clean state per test** so the class stays order-independent — truncate the tables
  the test touched in `@AfterEach`, reset WireMock stubs in `@BeforeEach`. A `static`
  `@Container` is already per-class; `@TestInstance(PER_CLASS)` is for sharing setup
  across the class, not for container lifecycle.
