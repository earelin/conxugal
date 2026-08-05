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
- **Assert what a write left in the table with AssertJ DB, never by reading it back
  through the repository under test.** A `findById` after an `insert`/`update`/`delete`
  only shows two methods of the same adapter agreeing — it cannot see the rows the
  statement should not have touched, so an unscoped `UPDATE`/`DELETE` still passes.
  Build the table with `AssertDbConnectionFactory.of(dataSource).create().table("...")`,
  `.columnsToOrder(new Table.Order[] {Table.Order.asc("name")})` (an array, not varargs)
  so `row(n)` is stable, then assert `hasNumberOfRows(n)` and **every** row the test set
  up, not just the one written to. Exception: when the read *is* the method under test
  (`findById`, `findAllOrderByName`, ordering), assert its return value. Everything else
  is AssertJ.
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
- **Clean state per test** so the class stays order-independent — call
  `DatabaseCleanup.truncateAllTables(dataSource)` in `@AfterEach`, reset WireMock stubs in
  `@BeforeEach`. A `static` `@Container` is already per-class; `@TestInstance(PER_CLASS)`
  is for sharing setup across the class, not for container lifecycle.
- **Never list the tables to truncate** — `DatabaseCleanup` discovers them. A per-class list
  breaks unrelated suites the moment a new table takes a foreign key to one of them. It also
  takes a raw `Connection`, for tests that drive one off the container directly.
