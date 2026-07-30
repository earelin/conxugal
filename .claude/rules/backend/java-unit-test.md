---
paths:
  - "server/{commons,domain,application,infrastructure}/src/test/java/**/*Test.java"
---

# Java unit tests

JUnit 5 tests for a Java class in isolation — no application context, database,
network, or filesystem. Substitute collaborators with test doubles, and keep tests
deterministic: inject clocks, seeds, and IDs rather than reading `now()` or random.

The modules are enumerated rather than globbed because the `acceptance` module and
the `architecture` module's ArchUnit tests also live under `src/test/java` but follow
their own conventions. Add a new module here when it gains unit tests.

- **Assert with AssertJ**, never JUnit's `assertEquals`/`assertTrue` or Hamcrest. Use
  the type-specific API — `containsExactly`, `hasSize`, `extracting`,
  `assertThatThrownBy` / `assertThatExceptionOfType` for exceptions.
- **Name test methods in snake_case**, describing behaviour rather than
  implementation — `throws_when_amount_is_negative`. No `test` prefix; add
  `@DisplayName` only when a sentence adds something the method name can't.
- **Double collaborators with Mockito under strict stubbing** —
  `@ExtendWith(MockitoExtension.class)` with `@Mock` fields, or `mock(Type.class)`.
  Never relax it: no `lenient()`, `@MockitoSettings(strictness = LENIENT)`, or
  `withSettings().lenient()`. An unused stub means the test or the code is wrong; fix
  the cause instead of silencing it.
- **Never `verify(...)`.** Configure return values and assert on the unit's output;
  under strict stubbing a stub you assert against already proves the collaborator was
  called with those arguments. Two exceptions: asserting that an interaction did *not*
  happen (`never()`, `verifyNoInteractions`), which stubbing cannot express, and a
  void side effect on a collaborator (a row written, an event published), which leaves
  nothing to assert on — `verify` the mock for that one call. Don't hand-roll a fake to
  capture it instead; a fake that has to reimplement the collaborator can quietly
  diverge from it and make every test that leans on it lie.
- **One behaviour per test**, structured Arrange / Act / Assert with a single
  assertion focus. Reach for `SoftAssertions` rather than sprawling unrelated checks.

Run with `./gradlew test` from `server/`.
