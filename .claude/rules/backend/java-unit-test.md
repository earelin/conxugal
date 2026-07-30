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
- **Prefer stubs over `verify(...)`.** Configure return values and assert on the
  unit's output. Strict stubbing already fails the test when a stubbed call never
  happens, so a stub you assert against proves the collaborator was called with those
  arguments — `verify(...)` would only restate it. For a genuine side effect with no
  return value (an event published, a row deleted), use a small recording fake and
  assert on what it captured.
- **One behaviour per test**, structured Arrange / Act / Assert with a single
  assertion focus. Reach for `SoftAssertions` rather than sprawling unrelated checks.

Run with `./gradlew test` from `server/`.
