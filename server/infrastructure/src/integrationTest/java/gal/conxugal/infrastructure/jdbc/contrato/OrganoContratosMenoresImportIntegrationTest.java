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
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
import gal.conxugal.domain.contrato.ContratosMenoresImportSummary;
import gal.conxugal.domain.contrato.ImportOrganoContratosMenores;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import io.micronaut.core.annotation.NonNull;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * One Órgano's walk against a real PostgreSQL and a stubbed source: what an interrupted import
 * retains, where a resumption picks up, and that re-reading changes nothing but the values a
 * publication itself changed.
 *
 * <p><strong>{@code transactional = false}</strong>, because the whole point is which writes
 * commit separately: contracts in one transaction, the cursor and the run's progress in their own.
 * The default would join them into one the test rolled back, and every claim here would hold for a
 * reason production does not have.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganoContratosMenoresImportIntegrationTest implements TestPropertyProvider {

  private static final Instant T_ZERO = Instant.parse("2026-08-07T09:00:00Z");
  private static final String SOURCE_KEY = "242";

  private static final LocalDate THREE_MONTHS_BACK = LocalDate.of(2026, 5, 7);
  private static final LocalDate SIX_MONTHS_BACK = LocalDate.of(2026, 2, 7);
  private static final LocalDate NINE_MONTHS_BACK = LocalDate.of(2025, 11, 7);

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  private final Map<LocalDate, List<ContratoMenorSourceEntry>> published = new HashMap<>();
  private final List<LocalDate> readWindows = new ArrayList<>();
  private LocalDate unreachableWindow;
  private long recordsTotal;

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
  ContratoMenorSource contratoMenorSource;

  @Inject
  ImportRunRepository importRuns;

  @BeforeEach
  void publishThreeContractsAcrossThreeWindows() {
    published.clear();
    readWindows.clear();
    unreachableWindow = null;
    recordsTotal = 3;
    published.put(THREE_MONTHS_BACK, List.of(entry(1, "Servizos eléctricos", "3630.00")));
    published.put(SIX_MONTHS_BACK, List.of(entry(2, "Subministración de papel", "480.50")));
    published.put(NINE_MONTHS_BACK, List.of(entry(3, "Mantemento de xardíns", "1200.00")));
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(1);
          if (from.equals(unreachableWindow)) {
            throw new ContratoMenorSourceUnavailableException("source is down");
          }
          readWindows.add(from);
          return new ContratoMenorSourcePage(
              published.getOrDefault(from, List.of()), recordsTotal);
        });
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  @Test
  void resumption_continues_from_the_cursor_reading_fewer_windows_than_restarting()
      throws Exception {
    OrganoId organoId = insertOrgano();
    interruptedWalkOf(organoId);
    readWindows.clear();

    ContratosMenoresImportSummary resumed = walk(organoId);

    assertThat(readWindows).containsExactly(NINE_MONTHS_BACK);
    assertThat(resumed.state()).isEqualTo(ContratosMenoresImportStatus.COMPLETE);
    assertThat(contratoTable()).hasNumberOfRows(3);
  }

  // The overlap ADR-0017 takes deliberately: the cursor is written after the batch commits, so a
  // crash in between leaves it behind what is stored and the resumption reads that window again.
  @Test
  void resumption_over_the_cursor_that_the_crash_left_behind_stores_nothing_twice()
      throws Exception {
    OrganoId organoId = insertOrgano();
    interruptedWalkOf(organoId);
    final UUID identityBeforeTheOverlap = storedIdentityOf(2);
    rewindCursorTo(THREE_MONTHS_BACK);
    readWindows.clear();

    walk(organoId);

    assertThat(readWindows).containsExactly(SIX_MONTHS_BACK, NINE_MONTHS_BACK);
    assertThat(contratoTable()).hasNumberOfRows(3);
    assertThat(storedIdentityOf(2)).isEqualTo(identityBeforeTheOverlap);
  }

  // SPEC-0007 R17 prunes run history, and an Órgano interrupted mid-import has no successful run
  // to protect its rows. The cursor lives with the Órgano precisely so that costs it nothing.
  @Test
  void resumption_is_unaffected_by_every_run_record_being_pruned() throws Exception {
    OrganoId organoId = insertOrgano();
    interruptedWalkOf(organoId);
    pruneEveryRunRecord();
    readWindows.clear();

    ContratosMenoresImportSummary resumed = walk(organoId);

    assertThat(readWindows).containsExactly(NINE_MONTHS_BACK);
    assertThat(resumed.state()).isEqualTo(ContratosMenoresImportStatus.COMPLETE);
    assertThat(contratoTable()).hasNumberOfRows(3);
  }

  // One window, not three: completeness is tested against the count already stored, so a walk over
  // a history it already holds is finished as soon as it has read anything at all. What matters is
  // that the window it did read added nothing and changed nothing.
  @Test
  void walking_the_same_published_history_twice_adds_nothing_the_second_time() throws Exception {
    OrganoId organoId = insertOrgano();
    walk(organoId);
    forgetHowFarTheImportGot();

    ContratosMenoresImportSummary second = walk(organoId);

    assertThat(second)
        .isEqualTo(new ContratosMenoresImportSummary(0, 1, ContratosMenoresImportStatus.COMPLETE));
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(3);
    assertThat(contratos).row(0).value("obxecto").isEqualTo("Servizos eléctricos");
    assertThat(contratos).row(1).value("obxecto").isEqualTo("Subministración de papel");
    assertThat(contratos).row(2).value("obxecto").isEqualTo("Mantemento de xardíns");
  }

  @Test
  void publication_whose_attributes_changed_at_the_source_is_refreshed_in_place() throws Exception {
    OrganoId organoId = insertOrgano();
    walk(organoId);
    final UUID identityBeforeTheChange = storedIdentityOf(1);
    published.put(
        THREE_MONTHS_BACK, List.of(entry(1, "Servizos eléctricos, corrixido", "4000.00")));
    forgetHowFarTheImportGot();

    walk(organoId);

    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(3);
    assertThat(contratos).row(0).value("obxecto").isEqualTo("Servizos eléctricos, corrixido");
    assertThat(contratos).row(0).value("amount").isEqualTo(new BigDecimal("4000.00"));
    assertThat(storedIdentityOf(1)).isEqualTo(identityBeforeTheChange);
  }

  /**
   * A walk that read two of the three windows and then lost the source, settled as the orchestrator
   * would settle it so the guard is free for the resumption to claim.
   */
  private void interruptedWalkOf(OrganoId organoId) {
    unreachableWindow = NINE_MONTHS_BACK;
    ImportRunId runId = claim(organoId);
    assertThatExceptionOfType(ContratoMenorSourceUnavailableException.class)
        .isThrownBy(() -> importContratosMenores.run(runId, organo(organoId)));
    importRuns.complete(runId, ImportRunState.FAILED, 0, 0);
    assertThat(contratoTable()).hasNumberOfRows(2);
    unreachableWindow = null;
  }

  private ContratosMenoresImportSummary walk(OrganoId organoId) {
    ImportRunId runId = claim(organoId);
    ContratosMenoresImportSummary summary =
        importContratosMenores.run(runId, organo(organoId));
    importRuns.complete(runId, ImportRunState.SUCCEEDED, 0, 0);
    return summary;
  }

  private ImportRunId claim(OrganoId organoId) {
    return importRuns
        .claim(Importer.CONTRATOS_MENORES, List.of(organoId))
        .orElseThrow(() -> new IllegalStateException("the import guard was already held"));
  }

  private static OrganoDeContratacion organo(OrganoId organoId) {
    return new OrganoDeContratacion(
        organoId, SOURCE_KEY, "Axencia Turismo de Galicia", true, true, null);
  }

  private static ContratoMenorSourceEntry entry(long sourceId, String obxecto, String amount) {
    return new ContratoMenorSourceEntry(
        sourceId,
        LocalDate.of(2026, 6, 1),
        obxecto,
        new Money(new BigDecimal(amount)),
        "1 mes",
        "ACME SL",
        "B12345678");
  }

  /** The cursor as a crash between a batch's commit and its cursor write would have left it. */
  private static void rewindCursorTo(LocalDate cursorDate) throws SQLException {
    execute(
        "UPDATE contrato_menor_import_state SET cursor_date = ?",
        statement -> statement.setObject(1, cursorDate));
  }

  private static void pruneEveryRunRecord() throws SQLException {
    execute("DELETE FROM import_run_organo");
    execute("DELETE FROM import_run");
  }

  /**
   * Drops the Órgano's state row, so the next walk is an initial import over a history it already
   * holds — which is what "the same import run twice" means once the first one completed.
   */
  private static void forgetHowFarTheImportGot() throws SQLException {
    execute("DELETE FROM contrato_menor_import_state");
  }

  private static Table contratoTable() {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table("contrato_menor")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("source_id")})
        .build();
  }

  private static UUID storedIdentityOf(long sourceId) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT id FROM contrato_menor WHERE source_id = ?")) {
      statement.setLong(1, sourceId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException(
              "No contract stored for source id %d".formatted(sourceId));
        }
        return resultSet.getObject("id", UUID.class);
      }
    }
  }

  private static OrganoId insertOrgano() throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, importable)"
            + " VALUES (uuidv7(), ?, ?, TRUE, TRUE) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, SOURCE_KEY);
      statement.setString(2, "Axencia Turismo de Galicia");
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(resultSet.getObject("id", UUID.class));
      }
    }
  }

  private static void execute(String sql) throws SQLException {
    execute(sql, statement -> statement.clearParameters());
  }

  private static void execute(String sql, StatementBinding binding) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      binding.bind(statement);
      statement.executeUpdate();
    }
  }

  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  @FunctionalInterface
  private interface StatementBinding {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
