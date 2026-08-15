package gal.conxugal.infrastructure.jdbc.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gal.conxugal.domain.contrato.ContratoMenor;
import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.VisibleContratoMenor;
import gal.conxugal.domain.contrato.VisibleContratoMenorRepository;
import gal.conxugal.domain.contrato.YearSelection;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The browse read against a real PostgreSQL. The port is injected rather than the adapter class,
 * because what a reader depends on is the page and the count it answers, not a method that happens
 * to be public.
 *
 * <p>The read is exercised in <strong>all four orderings</strong> everywhere the ordering could
 * change the answer. That is not repetition for its own sake: the stored statement carries none of
 * them — each arrives as the {@code Sort} on the {@code Pageable} — so the four are four different
 * statements by the time PostgreSQL sees them, and a defect in one of the four is invisible from
 * the other three.
 *
 * <p>The year facets are here too, and they take none of that: one distinct-value read with no
 * paging, no ordering key and no count. What they need instead is a fixture the plan assertions
 * beside this class cannot have — a contract holding an amount and an awardee but <em>no date</em>,
 * which is in both browse indexes under a null year and which a facet read that trusted the index's
 * own predicate would offer as a year, ahead of every real one.
 *
 * <p><strong>The emitted SQL is read back rather than assumed.</strong> Every statement the adapter
 * runs is logged, and the log is the only place the assembled clause is visible. It is what proves
 * the things the answers alone cannot distinguish: that each ordering names the columns it should
 * and ends with the source identifier in its key's direction, that exactly one {@code ORDER BY}
 * reaches the page, that none reaches the count, and that the predicate both statements carry is
 * the one whose plan the schema test beside this one pins.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcVisibleContratoMenorRepositoryIntegrationTest implements TestPropertyProvider {

  private static final YearSelection BROWSED_YEAR = YearSelection.of(2025);
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2025, 6, 15);
  private static final String OPERADOR_NAME = "Servizos Galegos SL";
  private static final String FISCAL_ID = "b-12345678 ";
  private static final String CANONICAL_FISCAL_ID = "B-12345678";

  // Every contract of a selection shares one publication date and repeats a handful of round
  // amounts, so both sort keys are ties almost everywhere and the tiebreaker is what orders them.
  private static final List<Money> ROUND_AMOUNTS =
      List.of(
          new Money(new BigDecimal("1000.00")),
          new Money(new BigDecimal("2500.00")),
          new Money(new BigDecimal("7500.00")));

  private static final String QUERY_LOG = "io.micronaut.data.query";
  private static final String PAGE_MARKER = "SELECT contrato_menor.source_id";
  private static final String COUNT_MARKER = "SELECT COUNT(*)";
  private static final String FACETS_MARKER = "SELECT DISTINCT publication_year";

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  VisibleContratoMenorRepository visibleContratoMenorRepository;

  @Inject
  ContratoMenorRepository contratoMenorRepository;

  @Inject
  ContratoMenorTestRepository testRepository;

  @Inject
  DataSource dataSource;

  private Logger queryLog;
  private ListAppender<ILoggingEvent> emitted;

  private OrganoId browsedOrgano;
  private OrganoId otherOrgano;
  private OperadorEconomico awardee;

  static List<BrowseOrdering> orderings() {
    return BrowseOrdering.all();
  }

  // The capture is installed before anything can throw, so the restore below always has a logger
  // to put back and never fails ahead of the truncate.
  @BeforeEach
  void seedAndCapture() throws Exception {
    queryLog = (Logger) LoggerFactory.getLogger(QUERY_LOG);
    emitted = new ListAppender<>();
    emitted.start();
    queryLog.addAppender(emitted);
    queryLog.setLevel(Level.DEBUG);
    queryLog.setAdditive(false);

    browsedOrgano = insertOrgano("browsed");
    otherOrgano = insertOrgano("other");
    awardee = insertOperador(FISCAL_ID);
  }

  @AfterEach
  void cleanUp() throws Exception {
    queryLog.setAdditive(true);
    queryLog.setLevel(null);
    queryLog.detachAppender(emitted);
    emitted.stop();
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  /**
   * The whole clause, and the paging immediately after it, so that the source identifier is proved
   * to come <em>last</em> rather than merely to appear.
   */
  @ParameterizedTest
  @MethodSource("orderings")
  void appends_the_whole_ordering_it_was_given_ending_with_the_source_identifier(
      BrowseOrdering ordering) {
    store(visibleBatch(browsedOrgano, 1L, 3));

    page(0, 50, ordering);

    assertThat(lastStatementContaining(PAGE_MARKER))
        .containsIgnoringWhitespaces("%s LIMIT ? OFFSET ?".formatted(ordering.orderBy()));
  }

  @ParameterizedTest
  @MethodSource("orderings")
  void contributes_no_ordering_of_its_own(BrowseOrdering ordering) {
    store(visibleBatch(browsedOrgano, 1L, 3));

    page(0, 50, ordering);

    assertThat(lastStatementContaining(PAGE_MARKER))
        .containsOnlyOnce("ORDER BY");
  }

  /**
   * The predicate whose plan the schema test pins is the predicate this read actually runs — the
   * two are the same bytes, not two spellings of one intention that could drift apart.
   */
  @Test
  void selects_on_the_predicate_the_schema_test_pins_the_plan_of() {
    store(visibleBatch(browsedOrgano, 1L, 3));

    firstPage(50);

    assertThat(lastStatementContaining(PAGE_MARKER))
        .contains(ContratoMenorVisibleBrowseSchemaIntegrationTest.VISIBLE_WHERE);
  }

  /**
   * The count is pinned whole rather than by its predicate, because what it must <em>not</em> carry
   * matters as much as what it must: the page's join is a no-op for a count and costs it the
   * index-only scan the schema test proves. Only byte-identity catches a join creeping back in.
   */
  @Test
  void counts_with_the_statement_the_schema_test_pins_the_plan_of() {
    store(visibleBatch(browsedOrgano, 1L, 3));

    firstPage(50);

    assertThat(lastStatementContaining(COUNT_MARKER))
        .isEqualTo(ContratoMenorVisibleBrowseSchemaIntegrationTest.SELECTION_COUNT);
  }

  @ParameterizedTest
  @MethodSource("orderings")
  void counts_the_selection_without_the_ordering_or_the_paging_the_page_carries(
      BrowseOrdering ordering) {
    store(visibleBatch(browsedOrgano, 1L, 3));

    page(0, 50, ordering);

    assertThat(lastStatementContaining(COUNT_MARKER))
        .doesNotContain("ORDER BY")
        .doesNotContain("LIMIT")
        .doesNotContain("JOIN");
  }

  /**
   * The refusals are the whole enforcement of the closed set — a native statement takes a name
   * verbatim — so they are tested rather than trusted. Without these, a later simplification of the
   * check would leave every other case here green.
   */
  @Test
  void refuses_an_ordering_naming_any_column_no_sort_key_could_have_produced() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> visibleContratoMenorRepository.page(
            browsedOrgano,
            BROWSED_YEAR,
            Pageable.from(0, 50, Sort.of(Sort.Order.asc("obxecto; DROP TABLE contrato_menor")))))
        .withMessageContaining("obxecto");
  }

  @Test
  void refuses_an_unordered_request_rather_than_paging_an_arbitrary_order() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> visibleContratoMenorRepository.page(
            browsedOrgano, BROWSED_YEAR, Pageable.from(0, 50)));
  }

  @Test
  void refuses_an_unpaged_request_rather_than_sending_negative_limits() {
    BrowseOrdering ordering = BrowseOrdering.all().getFirst();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> visibleContratoMenorRepository.page(
            browsedOrgano, BROWSED_YEAR, Pageable.from(ordering.sort())));
  }

  @ParameterizedTest
  @MethodSource("orderings")
  void pages_the_whole_selection_with_none_repeated_and_none_skipped(BrowseOrdering ordering) {
    store(visibleBatch(browsedOrgano, 1L, 250));

    List<VisibleContratoMenor> walked = walkEveryPage(40, ordering);

    assertThat(walked)
        .hasSize(250)
        .isSortedAccordingTo(ordering.comparator())
        .extracting(VisibleContratoMenor::sourceId)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(sourceIds(1L, 250));
  }

  @Test
  void reads_only_the_selected_organo_rather_than_every_organo_of_the_year() {
    store(visibleBatch(browsedOrgano, 1L, 5));
    store(visibleBatch(otherOrgano, 100L, 7));

    Page<VisibleContratoMenor> page = firstPage(50);

    assertThat(page.getTotalSize()).isEqualTo(5);
    assertThat(page.getContent())
        .extracting(VisibleContratoMenor::sourceId)
        .containsExactlyInAnyOrderElementsOf(sourceIds(1L, 5));
  }

  @Test
  void contracts_on_either_year_boundary_belong_to_that_year_and_leak_into_neither_neighbour() {
    store(
        List.of(
            contrato(1L, browsedOrgano, LocalDate.of(2024, 12, 31), ROUND_AMOUNTS.getFirst()),
            contrato(2L, browsedOrgano, LocalDate.of(2025, 1, 1), ROUND_AMOUNTS.getFirst()),
            contrato(3L, browsedOrgano, LocalDate.of(2025, 12, 31), ROUND_AMOUNTS.getFirst()),
            contrato(4L, browsedOrgano, LocalDate.of(2026, 1, 1), ROUND_AMOUNTS.getFirst())));

    assertThat(sourceIdsOf(YearSelection.of(2024))).containsExactly(1L);
    assertThat(sourceIdsOf(YearSelection.of(2025))).containsExactly(2L, 3L);
    assertThat(sourceIdsOf(YearSelection.of(2026))).containsExactly(4L);
  }

  @Test
  void every_page_reports_the_count_of_the_whole_selection_rather_than_of_the_page() {
    store(visibleBatch(browsedOrgano, 1L, 120));

    BrowseOrdering ordering = BrowseOrdering.all().getFirst();
    List<Page<VisibleContratoMenor>> pages =
        List.of(page(0, 50, ordering), page(1, 50, ordering), page(2, 50, ordering));

    assertThat(pages)
        .extracting(Page::getTotalSize)
        .containsExactly(120L, 120L, 120L);
    assertThat(pages)
        .extracting(page -> page.getContent().size())
        .containsExactly(50, 50, 20);
  }

  @Test
  void pages_beyond_the_last_are_empty_and_still_report_the_true_total() {
    store(visibleBatch(browsedOrgano, 1L, 120));

    Page<VisibleContratoMenor> beyond = page(9, 50, BrowseOrdering.all().getFirst());

    assertThat(beyond.getContent()).isEmpty();
    assertThat(beyond.getTotalSize()).isEqualTo(120);
  }

  @ParameterizedTest
  @MethodSource("orderings")
  void withholds_contracts_missing_any_of_the_three_from_every_page_and_every_count(
      BrowseOrdering ordering) {
    store(visibleBatch(browsedOrgano, 1L, 4));
    store(incompleteContracts());

    List<VisibleContratoMenor> walked = walkEveryPage(2, ordering);

    assertThat(walked)
        .extracting(VisibleContratoMenor::sourceId)
        .containsExactlyInAnyOrderElementsOf(sourceIds(1L, 4));
    assertThat(firstPage(2).getTotalSize()).isEqualTo(4);
  }

  @Test
  void the_contracts_it_withholds_are_still_stored_and_reachable_by_source_identifier() {
    store(incompleteContracts());

    assertThat(firstPage(50).getTotalSize()).isZero();
    assertThat(testRepository.findBySourceId(901L)).isPresent();
    assertThat(testRepository.findBySourceId(902L)).isPresent();
    assertThat(testRepository.findBySourceId(903L)).isPresent();
    assertThat(testRepository.findBySourceId(904L)).isPresent();
  }

  /**
   * The two values a row carries as types rather than as columns. The awardee's identifier is
   * asserted against its canonical form rather than the text stored, because rebuilding it is what
   * a reader is relying on when the type says {@code FiscalIdentifier} and not {@code String}.
   */
  @Test
  void reads_the_amount_and_the_awardee_onto_their_value_types() {
    store(List.of(contrato(1L, browsedOrgano, PUBLISHED_ON, new Money(new BigDecimal("1234.50")))));

    assertThat(firstPage(50).getContent())
        .singleElement()
        .satisfies(
            visible -> {
              assertThat(visible.amount()).isEqualTo(new Money(new BigDecimal("1234.50")));
              assertThat(visible.awardeeFiscalId())
                  .isEqualTo(new FiscalIdentifier(CANONICAL_FISCAL_ID));
              assertThat(visible.awardeeName()).isEqualTo(OPERADOR_NAME);
            });
  }

  // ------------------------------------------------------------------------ the year facets

  @Test
  void offers_the_years_the_organo_has_visible_contracts_in_newest_first() {
    store(publishedIn(1L, 2023));
    store(publishedIn(2L, 2025));
    store(publishedIn(3L, 2024));
    store(publishedIn(4L, 2025));

    assertThat(visibleContratoMenorRepository.years(browsedOrgano))
        .containsExactly(YearSelection.of(2025), YearSelection.of(2024), YearSelection.of(2023));
  }

  @Test
  void offers_no_year_for_an_organo_holding_no_contracts_at_all() {
    store(visibleBatch(otherOrgano, 100L, 5));

    assertThat(visibleContratoMenorRepository.years(browsedOrgano)).isEmpty();
  }

  /**
   * The facet half of the withholding rule, and the reason it needs its own case: an Órgano whose
   * only contract of a year is anomalous must not offer that year, or the chooser would open on a
   * selection that answers nothing.
   */
  @Test
  void offers_no_year_whose_only_contracts_are_anomalous() {
    store(publishedIn(1L, 2025));
    store(
        List.of(
            contratoAwardedTo(901L, browsedOrgano, LocalDate.of(2023, 4, 1), null, awardee),
            contratoAwardedTo(
                902L, browsedOrgano, LocalDate.of(2022, 4, 1), ROUND_AMOUNTS.getFirst(), null)));

    assertThat(visibleContratoMenorRepository.years(browsedOrgano))
        .containsExactly(YearSelection.of(2025));
  }

  /**
   * The conjunct this read alone has to carry. An undated contract holding both an amount and an
   * awardee enters the browse indexes under a null year, and descending order would offer that
   * null <em>ahead</em> of every real year — so the section would open on one that is not a year.
   */
  @Test
  void offers_nothing_for_an_organo_whose_contracts_all_lack_publication_dates() {
    store(
        List.of(
            contratoAwardedTo(901L, browsedOrgano, null, ROUND_AMOUNTS.getFirst(), awardee),
            contratoAwardedTo(902L, browsedOrgano, null, ROUND_AMOUNTS.getLast(), awardee)));

    assertThat(visibleContratoMenorRepository.years(browsedOrgano)).isEmpty();
  }

  @Test
  void an_undated_contract_costs_the_dated_ones_none_of_their_years() {
    store(publishedIn(1L, 2025));
    store(List.of(contratoAwardedTo(901L, browsedOrgano, null, ROUND_AMOUNTS.getFirst(), awardee)));

    assertThat(visibleContratoMenorRepository.years(browsedOrgano))
        .containsExactly(YearSelection.of(2025));
  }

  @Test
  void offers_only_the_years_of_the_organo_asked_about() {
    store(publishedIn(1L, 2025));
    store(List.of(contrato(100L, otherOrgano, LocalDate.of(2019, 3, 2), ROUND_AMOUNTS.getFirst())));

    assertThat(visibleContratoMenorRepository.years(browsedOrgano))
        .containsExactly(YearSelection.of(2025));
  }

  /**
   * The statement whose plan the schema test pins is the statement this read runs, asserted whole
   * rather than by its predicate: what it must <em>not</em> carry — a join, an alias, a second
   * ordering — is as load-bearing as what it must, and only byte-identity catches any of them.
   */
  @Test
  void reads_the_facets_with_the_statement_the_schema_test_pins_the_plan_of() {
    store(publishedIn(1L, 2025));

    visibleContratoMenorRepository.years(browsedOrgano);

    assertThat(lastStatementContaining(FACETS_MARKER))
        .isEqualTo(ContratoMenorVisibleBrowseSchemaIntegrationTest.YEAR_FACETS);
  }

  private List<ContratoMenor> publishedIn(long sourceId, int year) {
    return List.of(
        contrato(sourceId, browsedOrgano, LocalDate.of(year, 6, 15), ROUND_AMOUNTS.getFirst()));
  }

  private Page<VisibleContratoMenor> page(int number, int size, BrowseOrdering ordering) {
    return visibleContratoMenorRepository.page(
        browsedOrgano, BROWSED_YEAR, Pageable.from(number, size, ordering.sort()));
  }

  private Page<VisibleContratoMenor> firstPage(int size) {
    return page(0, size, BrowseOrdering.all().getFirst());
  }

  private List<VisibleContratoMenor> walkEveryPage(int size, BrowseOrdering ordering) {
    Page<VisibleContratoMenor> first = page(0, size, ordering);
    List<VisibleContratoMenor> walked = new ArrayList<>(first.getContent());
    for (int number = 1; number < first.getTotalPages(); number++) {
      walked.addAll(page(number, size, ordering).getContent());
    }
    return walked;
  }

  private List<Long> sourceIdsOf(YearSelection year) {
    BrowseOrdering ordering = BrowseOrdering.all().getFirst();
    return visibleContratoMenorRepository
        .page(browsedOrgano, year, Pageable.from(0, 50, ordering.sort()))
        .getContent()
        .stream()
        .map(VisibleContratoMenor::sourceId)
        .toList();
  }

  private static List<Long> sourceIds(long firstSourceId, int count) {
    return LongStream.range(firstSourceId, firstSourceId + count)
        .boxed()
        .toList();
  }

  private void store(List<ContratoMenor> contratos) {
    contratoMenorRepository.upsertAll(contratos);
  }

  private List<ContratoMenor> visibleBatch(OrganoId organoId, long firstSourceId, int count) {
    List<ContratoMenor> batch = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      batch.add(
          contrato(
              firstSourceId + index,
              organoId,
              PUBLISHED_ON,
              ROUND_AMOUNTS.get(index % ROUND_AMOUNTS.size())));
    }
    return batch;
  }

  // One missing its date, one its amount, one its awardee, and one missing all three — the last is
  // what catches a count query that dropped a conjunct the page kept.
  private List<ContratoMenor> incompleteContracts() {
    return List.of(
        contratoAwardedTo(901L, browsedOrgano, null, ROUND_AMOUNTS.getFirst(), awardee),
        contratoAwardedTo(902L, browsedOrgano, PUBLISHED_ON, null, awardee),
        contratoAwardedTo(903L, browsedOrgano, PUBLISHED_ON, ROUND_AMOUNTS.getFirst(), null),
        contratoAwardedTo(904L, browsedOrgano, null, null, null));
  }

  private ContratoMenor contrato(
      long sourceId, OrganoId organoId, LocalDate publicationDate, Money amount) {
    return contratoAwardedTo(sourceId, organoId, publicationDate, amount, awardee);
  }

  private static ContratoMenor contratoAwardedTo(
      long sourceId,
      OrganoId organoId,
      @Nullable LocalDate publicationDate,
      @Nullable Money amount,
      @Nullable OperadorEconomico awardee) {
    return new ContratoMenor(
        sourceId,
        organoId,
        publicationDate,
        "Subministración de material",
        amount,
        "1 mes",
        awardee);
  }

  /**
   * The statement itself, with the log's own prefix taken off, so an assertion can be about SQL
   * rather than about how Micronaut Data words a log line.
   */
  private String lastStatementContaining(String marker) {
    return emitted.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(message -> message.contains(marker))
        .map(message -> message.substring(message.indexOf(marker)))
        .reduce((earlier, later) -> later)
        .orElseThrow(() -> new AssertionError("No statement containing [%s] was logged. Logged: %s"
            .formatted(marker, emitted.list)));
  }

  // Returns the awardee it stored rather than only its id, so a contract can never be built
  // against a fiscal identifier the row does not carry.
  private OperadorEconomico insertOperador(String fiscalId) throws Exception {
    FiscalIdentifier canonical = new FiscalIdentifier(fiscalId);
    String sql =
        "INSERT INTO operador_economico (id, fiscal_id, name, name_rank_date, name_rank_source_id)"
            + " VALUES (uuidv7(), ?, ?, ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, canonical.value());
      statement.setString(2, OPERADOR_NAME);
      statement.setObject(3, Date.valueOf(PUBLISHED_ON));
      statement.setLong(4, 4711L);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OperadorEconomico(
            new OperadorId(resultSet.getObject("id", UUID.class)),
            canonical,
            OPERADOR_NAME,
            new NomeRank(PUBLISHED_ON, 4711L),
            Set.of());
      }
    }
  }

  // Neither helper commits: the injected DataSource is Micronaut Data's connection-context-aware
  // proxy, so the adapter under test shares this connection and sees the rows uncommitted.
  private OrganoId insertOrgano(String sourceKey) throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active)"
            + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, sourceKey);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(resultSet.getObject("id", UUID.class));
      }
    }
  }
}
