---
paths:
  - "server/{commons,domain,application,infrastructure}/src/test/java/**/*Test.java"
---

# Java unit tests

JUnit 5 tests for a Java class in isolation — no application context, database,
network, or filesystem. Assert with **AssertJ**, double collaborators with
**Mockito** under strict stubbing, name test methods in **snake_case**, and prefer
stubs and recording fakes over mocks and `verify(...)`.

Scope note: the modules are enumerated rather than globbed because the `acceptance`
module and the `architecture` module's ArchUnit tests also live under
`src/test/java` but follow their own conventions. Add a new module here when it
gains unit tests. Integration tests need no exclusion — they live in the
`src/integrationTest` source set.

## Rules

- **Assertions use AssertJ.** Always `assertThat(actual)...`; never JUnit's
  `assertEquals`, `assertTrue`, or Hamcrest. Use the fluent, type-specific API —
  `isEqualTo`, `containsExactly`, `hasSize`, `isEmpty`, `extracting`, and
  `assertThatThrownBy` / `assertThatExceptionOfType` for exceptions.
- **Test method names are snake_case** and describe behaviour, not implementation —
  e.g. `returns_empty_list_when_no_orders_exist`, `throws_when_amount_is_negative`.
  No `test` prefix. Annotate with `@Test`; add `@DisplayName` only when a
  human-readable sentence adds value beyond the method name.
- **Test doubles use Mockito with strict stubbing.** Prefer annotation-driven setup
  (`@ExtendWith(MockitoExtension.class)` with `@Mock` fields) or `mock(Type.class)` —
  the extension enables Mockito's default `STRICT_STUBS`. **Never relax it:** no
  `lenient()`, `@MockitoSettings(strictness = Strictness.LENIENT)`, or
  `withSettings().lenient()`. An unnecessary or unused stub is a signal the test or
  the code is wrong — fix the cause, do not silence it with leniency.
- **Prefer stubs, and let strict stubbing prove interactions instead of
  `verify(...)`.** Default to configuring return values with
  `when(dep.call()).thenReturn(...)` (or `doReturn`) and asserting on the unit's
  output — state verification. Because strict stubbing fails the test when a stubbed
  call never happens, a stub you assert against *already* proves the collaborator was
  called with those arguments; a `verify(...)` would only restate it. When the
  contract is a genuine side effect with no return value (an event published, a row
  deleted), prefer a **recording fake** — a small hand-written double that captures
  the interaction — and assert on it with AssertJ.
- **One behaviour per test.** Structure each test as Arrange / Act / Assert (Given /
  When / Then), with a single logical assertion focus. Use AssertJ's soft assertions
  (`SoftAssertions` / `assertThatCode`) rather than sprawling unrelated checks.
- **Keep it a unit test.** Substitute collaborators with stubs; no framework
  application context, database, network, or filesystem. Make tests deterministic —
  inject clocks, seeds, and IDs rather than reading `now()` or random inside the test.

## Format

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @InjectMocks
  private OrderService orderService;

  @Test
  void returns_total_of_all_orders_for_customer() {
    when(orderRepository.findByCustomer("c-1"))
        .thenReturn(List.of(new Order(10), new Order(15)));

    int total = orderService.totalFor("c-1");

    assertThat(total).isEqualTo(25);
  }

  @Test
  void throws_when_customer_id_is_blank() {
    assertThatThrownBy(() -> orderService.totalFor(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("customer");
  }

  @Test
  void publishes_event_when_order_is_placed() {
    RecordingEventPublisher events = new RecordingEventPublisher();
    OrderService service = new OrderService(orderRepository, events);

    service.place(new Order(10));

    assertThat(events.published()).singleElement().isInstanceOf(OrderPlacedEvent.class);
  }
}
```

## Steps

1. Read the class under test and note each collaborator and each observable output
   (return values, thrown exceptions, genuine side effects).
2. Enumerate behaviours to cover: happy path, boundaries and edge cases, and each
   error / exception path.
3. For each behaviour write a snake_case `@Test`: arrange by stubbing collaborators
   (strict stubbing, never `lenient()`), act on the unit, assert the output with
   AssertJ. Let strict stubbing prove interactions instead of `verify(...)`; for a
   pure side effect, assert on a recording fake.
4. Confirm the tests compile and pass (`./gradlew test`, run from `server/`), and
   summarise the behaviours covered.
