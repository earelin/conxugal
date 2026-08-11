package gal.conxugal.infrastructure.jdbc.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.db.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourcePage;
import gal.conxugal.domain.contrato.ImportOrganoContratosMenores;
import gal.conxugal.domain.contrato.StoreContratosMenoresBatch;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The awardee derived from a real walk against a real PostgreSQL: which awards become one operador
 * and which become two, the name it ends up displayed under, the names retained beside it, and what
 * survives a re-import.
 *
 * <p><strong>{@code transactional = false}</strong>, because two of these are about what committed:
 * a batch that failed part-way must leave no operador behind, and a re-import must leave every row
 * exactly as it found it. The default would wrap the lot in one transaction the test rolled back,
 * and both claims would hold for a reason production does not have.
 *
 * <p>Every award is published into the walk's first window, and the count the source reports is the
 * number of awards, so one window ends the walk. What is under test is the derivation, not the
 * shape of the walk.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratosMenoresDerivationIntegrationTest implements TestPropertyProvider {

  private static final Instant T_ZERO = Instant.parse("2026-08-07T09:00:00Z");
  private static final String SOURCE_KEY = "242";
  private static final LocalDate FIRST_WINDOW_START = LocalDate.of(2026, 5, 10);

  private static final LocalDate JANUARY = LocalDate.of(2026, 1, 10);
  private static final LocalDate FEBRUARY = LocalDate.of(2026, 2, 10);
  private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);

  private static final String CATALOGUE_TUPLE_VERSIONS =
      """
      SELECT 'operador:' || ctid::text AS version FROM operador_economico
      UNION ALL
      SELECT 'nome:' || ctid::text AS version FROM operador_economico_nome_alternativo
      ORDER BY version
      """;

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  private final List<ContratoMenorSourceEntry> published = new ArrayList<>();

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

  @MockBean(Clock.class)
  Clock clock() {
    return () -> T_ZERO;
  }

  @MockBean(ContratoMenorSource.class)
  ContratoMenorSource contratoMenorSourceMock() {
    return mock(ContratoMenorSource.class);
  }

  @Inject
  ImportOrganoContratosMenores importContratosMenores;

  @Inject
  StoreContratosMenoresBatch batch;

  @Inject
  ContratoMenorSource contratoMenorSource;

  @Inject
  ImportRunRepository importRuns;

  @BeforeEach
  void theSourcePublishesOneWindow() {
    published.clear();
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(1);
          return new ContratoMenorSourcePage(
              from.equals(FIRST_WINDOW_START) ? List.copyOf(published) : List.of(),
              published.size());
        });
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  // The lower-case spelling is imported first, so the canonical identifier the row ends up holding
  // cannot have come from whichever award happened to be read first.
  @Test
  void awards_whose_identifiers_differ_only_in_padding_or_case_share_one_operador()
      throws Exception {
    OrganoId organoId = insertOrgano();
    publishes(
        award(1L, JANUARY, "ACME SL", "b12345678"),
        award(2L, FEBRUARY, "ACME SL", " B12345678 "));

    walk(organoId);

    Table operadores = operadorTable();
    assertThat(operadores).hasNumberOfRows(1);
    assertThat(operadores).row(0).value("fiscal_id").isEqualTo("B12345678");
    UUID operadorId = operadorIdOf("B12345678");
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(2);
    assertThat(contratos).row(0).value("operador_economico_id").isEqualTo(operadorId);
    assertThat(contratos).row(1).value("operador_economico_id").isEqualTo(operadorId);
  }

  // Over-merging two suppliers into one is as damaging as splitting one in two, so the other side
  // of the matching rule is proved against the unique constraint that enforces it.
  @Test
  void awards_whose_identifiers_differ_in_any_other_way_are_two_operadores() throws Exception {
    OrganoId organoId = insertOrgano();
    publishes(
        award(1L, JANUARY, "ACME SL", "B12345678"),
        award(2L, JANUARY, "ACME SL", "B 12345678"),
        award(3L, JANUARY, "ACME SL", "B-12345678"),
        award(4L, JANUARY, "ACME SL", "B12345679"));

    walk(organoId);

    Table operadores = operadorTable();
    assertThat(operadores).hasNumberOfRows(4);
    assertThat(operadores).row(0).value("fiscal_id").isEqualTo("B 12345678");
    assertThat(operadores).row(1).value("fiscal_id").isEqualTo("B-12345678");
    assertThat(operadores).row(2).value("fiscal_id").isEqualTo("B12345678");
    assertThat(operadores).row(3).value("fiscal_id").isEqualTo("B12345679");
  }

  @Test
  void three_names_leave_the_most_recent_displayed_and_the_other_two_retained() throws Exception {
    OrganoId organoId = insertOrgano();
    publishes(
        award(1L, JANUARY, "Primeira SL", "B12345678"),
        award(2L, FEBRUARY, "Segunda SL", "B12345678"),
        award(3L, MARCH, "Terceira SL", "B12345678"));

    walk(organoId);

    Table operadores = operadorTable();
    assertThat(operadores).hasNumberOfRows(1);
    assertThat(operadores)
        .row(0)
            .value("name").isEqualTo("Terceira SL")
            .value("name_rank_date").isEqualTo(MARCH)
            .value("name_rank_source_id").isEqualTo(3);
    Table retained = nomeAlternativoTable();
    assertThat(retained).hasNumberOfRows(2);
    assertThat(retained)
        .row(0)
            .value("name").isEqualTo("Primeira SL")
            .value("last_published_date").isEqualTo(JANUARY)
            .value("last_published_source_id").isEqualTo(1);
    assertThat(retained)
        .row(1)
            .value("name").isEqualTo("Segunda SL")
            .value("last_published_date").isEqualTo(FEBRUARY)
            .value("last_published_source_id").isEqualTo(2);
  }

  // A name promoted, displaced by another and promoted again ends up in exactly one place. Two
  // successive promotions, because one alone cannot tell a name that moved from one that was
  // copied.
  @Test
  void name_promoted_displaced_and_promoted_again_is_held_once() throws Exception {
    OrganoId organoId = insertOrgano();
    publishes(
        award(1L, JANUARY, "ACME SL", "B12345678"),
        award(2L, FEBRUARY, "Denominación Provisional SL", "B12345678"),
        award(3L, MARCH, "ACME SL", "B12345678"));

    walk(organoId);

    assertThat(operadorTable()).row(0).value("name").isEqualTo("ACME SL");
    Table retained = nomeAlternativoTable();
    assertThat(retained).hasNumberOfRows(1);
    assertThat(retained)
        .row(0)
            .value("name").isEqualTo("Denominación Provisional SL")
            .value("last_published_source_id").isEqualTo(2);
  }

  // One row per distinct name however many awards publish it, carrying the most recent of them.
  // Asserted by row count, because the failure this guards is a table that grows per award.
  @Test
  void many_awards_under_one_name_retain_it_once_carrying_the_most_recent_of_their_dates()
      throws Exception {
    OrganoId organoId = insertOrgano();
    publishes(
        award(9L, LocalDate.of(2026, 4, 10), "Nova Denominación SL", "B12345678"),
        award(1L, JANUARY, "ACME SL", "B12345678"),
        award(2L, FEBRUARY, "ACME SL", "B12345678"),
        award(3L, MARCH, "ACME SL", "B12345678"));

    walk(organoId);

    Table retained = nomeAlternativoTable();
    assertThat(retained).hasNumberOfRows(1);
    assertThat(retained)
        .row(0)
            .value("name").isEqualTo("ACME SL")
            .value("last_published_date").isEqualTo(MARCH)
            .value("last_published_source_id").isEqualTo(3);
  }

  // Not just "the same values": the physical rows are untouched, so nothing was rewritten with
  // what it already held — which a value comparison could not tell from a name quietly re-dated.
  @Test
  void re_importing_the_same_awards_adds_moves_and_re_dates_nothing() throws Exception {
    OrganoId organoId = insertOrgano();
    publishes(
        award(1L, JANUARY, "Primeira SL", "B12345678"),
        award(2L, FEBRUARY, "Segunda SL", "B12345678"),
        award(3L, MARCH, "Terceira SL", "B12345678"));
    walk(organoId);
    final List<String> before = catalogueTupleVersions();
    forgetHowFarTheImportGot();

    walk(organoId);

    assertThat(operadorTable()).hasNumberOfRows(1);
    assertThat(nomeAlternativoTable()).hasNumberOfRows(2);
    assertThat(catalogueTupleVersions()).isEqualTo(before);
  }

  @Test
  void award_whose_published_identifier_changed_is_repointed_at_the_operador_it_now_names()
      throws Exception {
    OrganoId organoId = insertOrgano();
    publishes(award(1L, JANUARY, "ACME SL", "B12345678"));
    walk(organoId);
    publishes(award(1L, JANUARY, "ACME SL", "B87654321"));
    forgetHowFarTheImportGot();

    walk(organoId);

    assertThat(operadorTable()).hasNumberOfRows(2);
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(1);
    assertThat(contratos)
        .row(0)
            .value("operador_economico_id").isEqualTo(operadorIdOf("B87654321"));
  }

  // The branch the source is not expected to take: the award is stored, and because the schema is
  // normalised it records no awardee at all rather than an invented or shared one.
  @Test
  void award_with_no_usable_identifier_is_stored_under_no_operador_and_creates_none()
      throws Exception {
    OrganoId organoId = insertOrgano();
    publishes(award(1L, JANUARY, "ACME SL", "   "), award(2L, JANUARY, "ACME SL", null));

    walk(organoId);

    assertThat(operadorTable()).hasNumberOfRows(0);
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(2);
    assertThat(contratos).row(0).value("operador_economico_id").isNull();
    assertThat(contratos).row(1).value("operador_economico_id").isNull();
  }

  // Either both are there or neither is. The batch is failed on the contract side, after its
  // operadores were created, which is the only ordering that can leave a stored contract whose
  // operador does not exist.
  //
  // Three awards rather than one: with a single operador the test cannot tell a batch that rolled
  // back whole from one that committed each derivation as it went and only lost the last.
  @Test
  void batch_that_fails_on_its_contracts_leaves_no_operador_behind() throws Exception {
    OrganoId unknownOrgano = new OrganoId(UUID.randomUUID());
    List<ContratoMenorSourceEntry> awards =
        List.of(
            award(1L, JANUARY, "Primeira SL", "B11111111"),
            award(2L, FEBRUARY, "Segunda SL", "B22222222"),
            award(3L, MARCH, "Terceira SL", "B33333333"));

    assertThatExceptionOfType(DataAccessException.class)
        .isThrownBy(() -> batch.store(awards, unknownOrgano));

    assertThat(operadorTable()).hasNumberOfRows(0);
    assertThat(contratoTable()).hasNumberOfRows(0);
  }

  private void publishes(ContratoMenorSourceEntry... awards) {
    published.clear();
    published.addAll(List.of(awards));
  }

  private static ContratoMenorSourceEntry award(
      long sourceId,
      @Nullable LocalDate publicationDate,
      @Nullable String awardeeName,
      @Nullable String awardeeFiscalId) {
    return new ContratoMenorSourceEntry(
        sourceId,
        publicationDate,
        "Obxecto %d".formatted(sourceId),
        new Money(new BigDecimal("100.00")),
        "1 mes",
        awardeeName,
        awardeeFiscalId);
  }

  private void walk(OrganoId organoId) {
    ImportRunId runId =
        importRuns
            .claim(Importer.CONTRATOS_MENORES, List.of(organoId))
            .orElseThrow(() -> new IllegalStateException("the import guard was already held"));
    importContratosMenores.run(runId, organo(organoId), () -> true);
    importRuns.complete(runId, ImportRunState.SUCCEEDED, 0, 0);
  }

  private static OrganoDeContratacion organo(OrganoId organoId) {
    return new OrganoDeContratacion(
        organoId, SOURCE_KEY, "Axencia Turismo de Galicia", true, true, null);
  }

  /** Drops the Órgano's state row, so the next walk reads the whole history again. */
  private static void forgetHowFarTheImportGot() throws SQLException {
    execute("DELETE FROM contrato_menor_import_state");
  }

  /**
   * Every row's physical location, which PostgreSQL moves whenever a row is rewritten. It is what
   * tells a row left alone from one updated with the values it already had — {@code xmin} could
   * not, these writes being spread over transactions of their own.
   */
  private static List<String> catalogueTupleVersions() throws SQLException {
    List<String> versions = new ArrayList<>();
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(CATALOGUE_TUPLE_VERSIONS);
        ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        versions.add(rows.getString("version"));
      }
    }
    return versions;
  }

  private static UUID operadorIdOf(String fiscalId) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT id FROM operador_economico WHERE fiscal_id = ?")) {
      statement.setString(1, fiscalId);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException("No operador stored under %s".formatted(fiscalId));
        }
        return rows.getObject("id", UUID.class);
      }
    }
  }

  private static Table contratoTable() {
    return table("contrato_menor", "source_id");
  }

  private static Table operadorTable() {
    return table("operador_economico", "fiscal_id");
  }

  private static Table nomeAlternativoTable() {
    return table("operador_economico_nome_alternativo", "name");
  }

  // Off the container rather than the injected DataSource: with no ambient transaction every write
  // under test really commits, and these assertions are about what it committed.
  private static Table table(String name, String order) {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table(name)
        .columnsToOrder(new Table.Order[] {Table.Order.asc(order)})
        .build();
  }

  private static OrganoId insertOrgano() throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, importable)"
            + " VALUES (uuidv7(), ?, ?, TRUE, TRUE) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, SOURCE_KEY);
      statement.setString(2, "Axencia Turismo de Galicia");
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(rows.getObject("id", UUID.class));
      }
    }
  }

  private static void execute(String sql) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.executeUpdate();
    }
  }

  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
