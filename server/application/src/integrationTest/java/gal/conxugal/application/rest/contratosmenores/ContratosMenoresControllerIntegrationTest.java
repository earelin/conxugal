package gal.conxugal.application.rest.contratosmenores;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.contrato.ListContratosMenores;
import gal.conxugal.domain.contrato.SortKey;
import gal.conxugal.domain.contrato.VisibleContratoMenor;
import gal.conxugal.domain.contrato.VisibleContratoMenorRepository;
import gal.conxugal.domain.contrato.YearSelection;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.OrganoRepository;
import gal.conxugal.domain.user.User;
import io.micronaut.context.annotation.Property;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The HTTP layer of the browse read alone: what the parameters become, what the page becomes, and
 * what is refused before either happens. What a selection actually contains, how it is ordered and
 * what the count covers belong to the use case's and the adapter's own suites, which run against a
 * real database; the read is mocked here and answers with a row stating the selection it was asked
 * for, so an assertion about the ordering is an assertion about what came back.
 *
 * <p>The import client is deliberately pointed somewhere else for the whole class. Nothing here
 * calls it — the point is that every source link is composed from this feature's own property, and
 * this is the property whose value would otherwise leak into one.
 */
@MicronautTest
@Property(name = "micronaut.http.services.contratosdegalicia.url",
    value = "http://contratosdegalicia:8080")
class ContratosMenoresControllerIntegrationTest extends AuthenticationTestSupport {

  private static final OrganoId SANIDADE = new OrganoId(UUID.randomUUID());
  private static final long TOTAL_ITEMS = 1832L;
  private static final long SOURCE_ID = 1234567L;

  private final List<Pageable> pageables = new ArrayList<>();

  @Inject
  ListContratosMenores listContratosMenores;

  @MockBean(ListContratosMenores.class)
  ListContratosMenores listContratosMenoresMock() {
    return mock(ListContratosMenores.class);
  }

  // Mocking a concrete use case still has Micronaut resolve the real constructor's arguments, and
  // both of this one's are adapters wanting a datasource this suite deliberately runs without.
  @MockBean(OrganoRepository.class)
  OrganoRepository organosMock() {
    return mock(OrganoRepository.class);
  }

  @MockBean(VisibleContratoMenorRepository.class)
  VisibleContratoMenorRepository visibleContratosMock() {
    return mock(VisibleContratoMenorRepository.class);
  }

  @Test
  void authenticated_reader_gets_one_page_of_the_selection_with_both_totals(
      RequestSpecification spec) {
    answerWithTheSelectionAskedFor();

    Response response = readAs(spec, TestUserFactory.normalUser(), "?year=2025&page=3&size=50");

    assertThat(response.jsonPath().getInt("page")).isEqualTo(3);
    assertThat(response.jsonPath().getInt("size")).isEqualTo(50);
    assertThat(response.jsonPath().getLong("totalItems")).isEqualTo(TOTAL_ITEMS);
    assertThat(response.jsonPath().getInt("totalPages")).isEqualTo(37);
    assertThat(response.jsonPath().getList("items")).hasSize(1);
  }

  // The two defaults, which nothing else here states: a reader who asks only for a year gets the
  // first page of fifty. They are the client's starting point, so moving one silently would move
  // every first request in the application.
  @Test
  void omitting_the_paging_reads_the_first_page_of_fifty(RequestSpecification spec) {
    answerWithTheSelectionAskedFor();

    Response response = readAs(spec, TestUserFactory.normalUser(), "?year=2025");

    assertThat(response.jsonPath().getInt("page")).isEqualTo(1);
    assertThat(response.jsonPath().getInt("size")).isEqualTo(50);
    assertThat(pageables).hasSize(1);
    assertThat(pageables.getFirst().getNumber()).isZero();
    assertThat(pageables.getFirst().getSize()).isEqualTo(50);
  }

  // R2 grants this read to both roles, and the path does no role-dependent narrowing of its own.
  @Test
  void admin_and_user_alike_read_the_same_page(RequestSpecification spec) {
    answerWithTheSelectionAskedFor();

    Response asUser = readAs(spec, TestUserFactory.normalUser(), "?year=2025");
    Response asAdmin = readAs(spec, TestUserFactory.adminUser(), "?year=2025");

    assertThat(asAdmin.asString()).isEqualTo(asUser.asString());
  }

  @Test
  void every_row_carries_what_the_source_published_about_the_contract(RequestSpecification spec) {
    answerWithTheSelectionAskedFor();

    Response response = readAs(spec, TestUserFactory.normalUser(), "?year=2025");

    assertThat(response.jsonPath().getLong("items[0].sourceId")).isEqualTo(SOURCE_ID);
    assertThat(response.jsonPath().getString("items[0].publicationDate")).isEqualTo("2025-03-14");
    assertThat(response.jsonPath().getString("items[0].duration")).isEqualTo("1 mes");
    assertThat(response.jsonPath().getDouble("items[0].amount")).isEqualTo(14520.75);
    assertThat(response.jsonPath().getString("items[0].awardee.name"))
        .isEqualTo("Subministracións Galegas, S.L.");
    assertThat(response.jsonPath().getString("items[0].awardee.fiscalId")).isEqualTo("B12345678");
  }

  // Every row of this list belongs to the Órgano already open, and nothing consumes an operador
  // identity yet. Asserted on the absence of the keys, because the assertions above name the ones
  // they want and so cannot see one they did not ask for.
  @Test
  void row_states_neither_the_awarding_organo_nor_an_operador_identity(
      RequestSpecification spec) {
    answerWithTheSelectionAskedFor();

    Response response = readAs(spec, TestUserFactory.normalUser(), "?year=2025");

    assertThat(response.jsonPath().getMap("items[0]"))
        .doesNotContainKeys("organo", "organoId", "operadorId", "operadorEconomicoId");
  }

  // The whole reason the link has a property of its own: this one is pointed at the stub the
  // importer scrapes in development, preview and the end-to-end suite, and the public link must
  // not follow it there.
  @Test
  void source_link_ignores_the_host_the_importer_is_pointed_at(RequestSpecification spec) {
    answerWithTheSelectionAskedFor();

    Response response = readAs(spec, TestUserFactory.normalUser(), "?year=2025");

    assertThat(response.jsonPath().getString("items[0].sourceUrl"))
        .isEqualTo("https://www.contratosdegalicia.gal/licitacion?N=" + SOURCE_ID);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("offeredOrderings")
  void each_offered_ordering_reaches_the_read_it_names(
      String sort, String expected, RequestSpecification spec) {
    answerWithTheSelectionAskedFor();

    Response response = readAs(spec, TestUserFactory.normalUser(), "?year=2025&sort=" + sort);

    assertThat(response.jsonPath().getString("items[0].obxecto")).isEqualTo(expected);
  }

  private static Stream<Arguments> offeredOrderings() {
    return Stream.of(
        arguments("publicationDate,desc", "PUBLICATION_DATE DESC 2025"),
        arguments("publicationDate,asc", "PUBLICATION_DATE ASC 2025"),
        arguments("amount,desc", "AMOUNT DESC 2025"),
        arguments("amount,asc", "AMOUNT ASC 2025"));
  }

  @Test
  void omitting_the_sort_reads_newest_published_first(RequestSpecification spec) {
    answerWithTheSelectionAskedFor();

    Response response = readAs(spec, TestUserFactory.normalUser(), "?year=2025");

    assertThat(response.jsonPath().getString("items[0].obxecto"))
        .isEqualTo("PUBLICATION_DATE DESC 2025");
  }

  // The ordering travels as two enums and is built once, in the use case. A pageable arriving with
  // a sort of its own would be a second source for the one clause the browse statement takes, and
  // the page number crossing the boundary has to be the store's, not the contract's.
  @Test
  void pageable_handed_down_is_zero_based_and_carries_no_ordering(RequestSpecification spec) {
    answerWithTheSelectionAskedFor();

    readAs(spec, TestUserFactory.normalUser(), "?year=2025&page=3&size=25");

    assertThat(pageables).hasSize(1);
    assertThat(pageables.getFirst().getNumber()).isEqualTo(2);
    assertThat(pageables.getFirst().getSize()).isEqualTo(25);
    assertThat(pageables.getFirst().getSort().isSorted()).isFalse();
  }

  // Out of range is a legitimate question about a real selection, so it is answered rather than
  // refused — and what it answers with is the selection's own totals, which is what lets a client
  // clamp to the last page instead of guessing where it is.
  @Test
  void page_beyond_the_last_answers_an_empty_one_with_the_selections_true_totals(
      RequestSpecification spec) {
    when(listContratosMenores.list(any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> Page.of(List.of(), invocation.getArgument(4), TOTAL_ITEMS));

    Response response = readAs(spec, TestUserFactory.normalUser(), "?year=2025&page=100&size=50");

    assertThat(response.jsonPath().getList("items")).isEmpty();
    assertThat(response.jsonPath().getInt("page")).isEqualTo(100);
    assertThat(response.jsonPath().getLong("totalItems")).isEqualTo(TOTAL_ITEMS);
    assertThat(response.jsonPath().getInt("totalPages")).isEqualTo(37);
  }

  @Test
  void unknown_organo_is_organo_not_found(RequestSpecification spec) {
    when(listContratosMenores.list(any(), any(), any(), any(), any()))
        .thenThrow(new OrganoNotFoundException(SANIDADE));

    Response response = getAs(spec, TestUserFactory.normalUser(), "?year=2025");

    assertProblem(response)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasType("urn:conxugal:problem-type:organo-not-found");
  }

  @Test
  void unauthenticated_caller_is_unauthorized(RequestSpecification spec) {
    given(spec)
    .when()
        .get(contractsOf(SANIDADE) + "?year=2025")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  // --- What the edge refuses before the read ever sees it ---------------------
  // Every one of these is refused rather than corrected. A selection quietly answered under a
  // different ordering, year or page would be a wrong answer nothing on the wire could reveal:
  // the envelope states none of the three back, the URL being what carries them.

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusedSelections")
  void selection_is_refused(String reason, String query, RequestSpecification spec) {
    Response response = getAs(spec, TestUserFactory.normalUser(), query);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(listContratosMenores);
  }

  private static Stream<Arguments> refusedSelections() {
    return Stream.of(
        arguments("year is absent, and there is no all-years list to fall back to", ""),
        arguments("year is empty", "?year="),
        arguments("year is not a year", "?year=20x5"),
        arguments("year is not four digits", "?year=205"),
        arguments("year is a word this system does not offer", "?year=undated"),
        arguments("sort carries no comma at all", "?year=2025&sort=obxecto"),
        arguments("sort names a property no ordering offers", "?year=2025&sort=obxecto,asc"),
        arguments("sort names a column the store really has", "?year=2025&sort=source_id,asc"),
        arguments("sort names a longer spelling of a direction",
            "?year=2025&sort=amount,descending"),
        arguments("sort is empty", "?year=2025&sort="),
        arguments("sort names no direction", "?year=2025&sort=amount"),
        arguments("sort names two directions", "?year=2025&sort=amount,asc,desc"),
        arguments("page is below the first one", "?year=2025&page=0"),
        arguments("page is negative", "?year=2025&page=-1"),
        arguments("page is not a number", "?year=2025&page=first"),
        arguments("size is below one", "?year=2025&size=0"),
        arguments("size is above the hundred allowed", "?year=2025&size=101"),
        arguments("a parameter this operation does not have", "?year=2025&order=amount"),
        arguments("sort is misspelt, which is the quietest way to get the wrong ordering",
            "?year=2025&srot=amount,asc"));
  }

  // Five rules can refuse a request here, and a status alone would leave a caller to work out
  // which. The detail is what the framework's own status handling drops, and why these refusals
  // are thrown as problems rather than as statuses.
  @Test
  void refusal_says_which_rule_the_selection_broke(RequestSpecification spec) {
    Response response =
        getAs(spec, TestUserFactory.normalUser(), "?year=2025&sort=obxecto,asc");

    assertProblem(response)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasDetail("sort must order by publicationDate or amount");
  }

  // The one refusal whose detail is not a constant. A parameter name is a stranger's text, of
  // whatever length and characters a URI admits, and the problem document declares its detail a
  // single bounded line — so what comes back names one parameter, flattened and short, rather
  // than however much was sent.
  @Test
  void refusal_repeats_back_one_short_parameter_name_however_much_was_sent(
      RequestSpecification spec) {
    String sprawling = "?year=2025&%0Aorder=1&" + "z".repeat(200) + "=1";

    Response response = getAs(spec, TestUserFactory.normalUser(), sprawling);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(response.jsonPath().getString("detail"))
        .doesNotContain("\n")
        .hasSizeLessThan(80);
  }

  /**
   * Answers with one row stating the selection the read was asked for, so that what the ordering
   * became is asserted on the response rather than on the mock.
   */
  private void answerWithTheSelectionAskedFor() {
    when(listContratosMenores.list(any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> {
          SortKey key = invocation.getArgument(2);
          Sort.Order.Direction direction = invocation.getArgument(3);
          YearSelection year = invocation.getArgument(1);
          Pageable pageable = invocation.getArgument(4);
          pageables.add(pageable);
          return Page.of(
              List.of(rowStating("%s %s %d".formatted(key, direction, year.year()))),
              pageable,
              TOTAL_ITEMS);
        });
  }

  private static VisibleContratoMenor rowStating(String selection) {
    return new VisibleContratoMenor(
        SOURCE_ID,
        LocalDate.of(2025, 3, 14),
        selection,
        new Money(new BigDecimal("14520.75")),
        "1 mes",
        "Subministracións Galegas, S.L.",
        new FiscalIdentifier("B12345678"));
  }

  /** The read as an authenticated caller, whatever it answers. */
  private Response getAs(RequestSpecification spec, User user, String query) {
    String sessionCookie = seedUserAndLoginAs(spec, user);

    return given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get(contractsOf(SANIDADE) + query);
  }

  /** The same read, for the cases that are only interesting once it succeeded. */
  private Response readAs(RequestSpecification spec, User user, String query) {
    Response response = getAs(spec, user, query);
    response.then().statusCode(HttpStatus.OK.getCode());
    return response;
  }

  private static String contractsOf(OrganoId organoId) {
    return "/api/organo/" + organoId + "/contratos-menores";
  }
}
