package gal.conxugal.infrastructure.jdbc.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.db.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourcePage;
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
import gal.conxugal.domain.contrato.ImportContratosMenores;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotEligibleForImportException;
import gal.conxugal.domain.time.Clock;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.LongStream;
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
 * What a multi-Órgano run leaves behind in the record, against a real PostgreSQL and a stubbed
 * source: which Órganos it covers and when they are written, how each of them ended, and the
 * verdict the whole run settles at.
 *
 * <p>The orchestration rules themselves are proved with the ports stubbed. What needs a database
 * is the record — the coverage rows enumerated before anything is read, the reasons stored beside
 * the states, and a verdict that survives an Órgano failing part-way.
 *
 * <p><strong>{@code transactional = false}</strong>, because the writes committing separately is
 * the whole claim: the claim, each batch, each settlement and the verdict are transactions of their
 * own, and the default would join them into one the test rolled back.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratosMenoresRunRecordIntegrationTest implements TestPropertyProvider {

  private static final Instant T_ZERO = Instant.parse("2026-08-07T09:00:00Z");

  private static final LocalDate FIRST_WINDOW_START = LocalDate.of(2026, 5, 10);
  private static final LocalDate SECOND_WINDOW_START = LocalDate.of(2026, 2, 10);
  private static final LocalDate THIRD_WINDOW_START = LocalDate.of(2025, 11, 13);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  private final Map<String, Map<LocalDate, List<ContratoMenorSourceEntry>>> published =
      new HashMap<>();
  private final Map<String, Long> recordsTotals = new HashMap<>();
  private final Set<String> unreachable = new HashSet<>();
  private final List<String> fetchedSourceKeys = new ArrayList<>();
  private BiConsumer<String, LocalDate> whileServingThePage = (sourceKey, windowStart) -> {};

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
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
  ImportContratosMenores importContratosMenores;

  @Inject
  ContratoMenorSource contratoMenorSource;

  @BeforeEach
  void resetTheSource() {
    published.clear();
    recordsTotals.clear();
    unreachable.clear();
    fetchedSourceKeys.clear();
    whileServingThePage = (sourceKey, windowStart) -> {};
    when(contratoMenorSource.fetchPage(anyString(), any(), any(), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          String sourceKey = invocation.getArgument(0);
          LocalDate windowStart = invocation.getArgument(1);
          if (unreachable.contains(sourceKey)) {
            throw new ContratoMenorSourceUnavailableException(
                "source is down for %s".formatted(sourceKey));
          }
          fetchedSourceKeys.add(sourceKey);
          whileServingThePage.accept(sourceKey, windowStart);
          return new ContratoMenorSourcePage(
              published.getOrDefault(sourceKey, Map.of()).getOrDefault(windowStart, List.of()),
              recordsTotals.getOrDefault(sourceKey, 0L));
        });
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  // Enumerated at the claim rather than discovered as the run reaches them, so a run that dies at
  // the fortieth of four hundred can still name what it set out to cover.
  @Test
  void covered_organos_are_written_before_any_of_them_is_read() throws Exception {
    final OrganoId eligible = insertOrgano("eligible", true, true);
    final OrganoId alsoEligible = insertOrgano("also-eligible", true, true);
    insertOrgano("marked-but-inactive", false, true);
    insertOrgano("active-but-unmarked", true, false);

    importContratosMenores.claimAll();

    Table coverage = coverageTable();
    assertThat(coverage).hasNumberOfRows(2);
    assertThat(coverage).column("organo_id").containsValues(eligible.value(), alsoEligible.value());
    assertThat(coverage).column("state").containsValues("PENDING", "PENDING");
    assertThat(contratoTable()).isEmpty();
    assertThat(fetchedSourceKeys).isEmpty();
  }

  @Test
  void run_where_one_organos_source_fails_is_partially_succeeded_and_names_it() throws Exception {
    final OrganoId first = insertOrgano("first", true, true);
    final OrganoId failing = insertOrgano("failing", true, true);
    final OrganoId last = insertOrgano("last", true, true);
    publishes("first", 1);
    publishes("last", 2);
    unreachable.add("failing");

    run();

    assertRunState("PARTIALLY_SUCCEEDED");
    assertThat(coverageTable()).hasNumberOfRows(3);
    assertCoverage(first, "SUCCEEDED");
    assertCoverage(failing, "FAILED");
    assertCoverage(last, "SUCCEEDED");
    assertThat(reasonFor(failing)).isNotNull();
    // The Órgano before the failure keeps what it stored, and the one after it is still imported.
    assertThat(contratoTable()).hasNumberOfRows(2);
    // Counted once, per Órgano as its batch committed, and totalled on the run. Settling with the
    // walk's own totals instead would add every contract a second time.
    assertThat(addedFor(first)).isOne();
    assertThat(addedFor(failing)).isZero();
    assertThat(addedFor(last)).isOne();
    Table runs = runTable();
    assertThat(runs).row(0).value("added").isEqualTo(2);
    assertThat(runs).row(0).value("refreshed").isEqualTo(0);
  }

  @Test
  void run_where_every_covered_organo_failed_is_recorded_failed() throws Exception {
    insertOrgano("first", true, true);
    insertOrgano("second", true, true);
    unreachable.add("first");
    unreachable.add("second");

    run();

    assertRunState("FAILED");
    assertThat(coverageTable()).column("state").containsValues("FAILED", "FAILED");
  }

  @Test
  void run_where_every_covered_organo_succeeded_is_recorded_succeeded() throws Exception {
    insertOrgano("first", true, true);
    insertOrgano("second", true, true);
    publishes("first", 1);
    publishes("second", 2);

    run();

    assertRunState("SUCCEEDED");
    assertThat(coverageTable()).column("state").containsValues("SUCCEEDED", "SUCCEEDED");
  }

  // Nothing was imported and nothing failed. This is the ordinary state of a loaded catalogue, and
  // reporting it as a failure every night is exactly what the skipped state exists to avoid.
  @Test
  void run_where_every_covered_organo_is_already_loaded_is_recorded_succeeded() throws Exception {
    OrganoId first = insertOrgano("first", true, true);
    OrganoId second = insertOrgano("second", true, true);
    insertImportState(first, "COMPLETE");
    insertImportState(second, "COMPLETE");

    run();

    assertRunState("SUCCEEDED");
    Table coverage = coverageTable();
    assertThat(coverage).column("state").containsValues("SKIPPED", "SKIPPED");
    assertThat(reasonFor(first)).contains("INCREMENTAL");
    assertThat(fetchedSourceKeys).isEmpty();
  }

  // The administrator withdraws the mark while the first batch is in flight. Everything that batch
  // stored stands, the Órgano stays incomplete, and the run itself is not a failure.
  @Test
  void unmarking_an_organo_mid_run_stops_it_keeping_everything_it_stored() throws Exception {
    OrganoId organoId = insertOrgano("first", true, true);
    publishesAcrossEveryWindow("first");
    whileServingThePage = (sourceKey, windowStart) -> unmark(organoId);

    run();

    assertRunState("SUCCEEDED");
    assertCoverage(organoId, "STOPPED");
    assertThat(contratoTable()).hasNumberOfRows(1);
    Table states = importStateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states).row(0).value("state").isEqualTo("INCOMPLETE");
    assertThat(states).row(0).value("cursor_date").isEqualTo(FIRST_WINDOW_START);
  }

  // Incomplete is what makes the difference: a re-marked Órgano picks up at its cursor rather than
  // being treated as up to date, and re-reading the window it was cut off in stores nothing twice.
  @Test
  void re_marking_the_stopped_organo_resumes_it_rather_than_restarting() throws Exception {
    OrganoId organoId = insertOrgano("first", true, true);
    publishesAcrossEveryWindow("first");
    whileServingThePage = (sourceKey, windowStart) -> unmark(organoId);
    run();
    whileServingThePage = (sourceKey, windowStart) -> {};
    execute("UPDATE organo_contratacion SET importable = TRUE WHERE id = ?", organoId.value());
    fetchedSourceKeys.clear();

    run();

    assertThat(contratoTable()).hasNumberOfRows(3);
    Table states = importStateTable();
    assertThat(states).row(0).value("state").isEqualTo("COMPLETE");
    // Two requests, not three: the window the stopped run read out is not read a second time, so
    // the resumption picks up at the cursor rather than starting the history again.
    assertThat(fetchedSourceKeys).hasSize(2);
  }

  @Test
  void single_organo_scope_naming_an_unmarked_organo_records_no_run() throws Exception {
    OrganoId organoId = insertOrgano("unmarked", true, false);

    assertThatExceptionOfType(OrganoNotEligibleForImportException.class)
        .isThrownBy(() -> importContratosMenores.claimOrgano(organoId));

    assertThat(runTable()).isEmpty();
    assertThat(coverageTable()).isEmpty();
  }

  // Serial: the source is asked, mid-page, how many Órganos the run has in progress at that moment.
  @Test
  void no_two_covered_organos_are_imported_at_the_same_time() throws Exception {
    insertOrgano("first", true, true);
    insertOrgano("second", true, true);
    insertOrgano("third", true, true);
    publishesAcrossEveryWindow("first");
    publishesAcrossEveryWindow("second");
    publishesAcrossEveryWindow("third");
    List<Integer> inProgress = new ArrayList<>();
    whileServingThePage = (sourceKey, windowStart) -> inProgress.add(countInProgress());

    run();

    assertThat(inProgress).isNotEmpty().allMatch(count -> count <= 1);
  }

  private void run() {
    importContratosMenores.execute(importContratosMenores.claimAll());
  }

  /** One window's worth of history, so the walk reads the Órgano out in a single request. */
  private void publishes(String sourceKey, long... sourceIds) {
    published.put(sourceKey, Map.of(FIRST_WINDOW_START, entries(sourceIds)));
    recordsTotals.put(sourceKey, (long) sourceIds.length);
  }

  /** One contract in each of the three windows, so a walk can be interrupted part-way through. */
  private void publishesAcrossEveryWindow(String sourceKey) {
    long base = sourceKey.hashCode() & 0xffff;
    published.put(
        sourceKey,
        Map.of(
            FIRST_WINDOW_START, entries(base + 1),
            SECOND_WINDOW_START, entries(base + 2),
            THIRD_WINDOW_START, entries(base + 3)));
    recordsTotals.put(sourceKey, 3L);
  }

  private static List<ContratoMenorSourceEntry> entries(long... sourceIds) {
    return LongStream.of(sourceIds)
        .mapToObj(
            sourceId ->
                new ContratoMenorSourceEntry(
                    sourceId,
                    LocalDate.of(2026, 6, 1),
                    "Obxecto %d".formatted(sourceId),
                    new Money(new BigDecimal("100.00")),
                    "1 mes",
                    "ACME SL",
                    "B12345678"))
        .toList();
  }

  private static void unmark(OrganoId organoId) {
    try {
      execute("UPDATE organo_contratacion SET importable = FALSE WHERE id = ?", organoId.value());
    } catch (SQLException e) {
      throw new IllegalStateException("could not withdraw the import mark", e);
    }
  }

  private static int countInProgress() {
    String sql = "SELECT count(*) FROM import_run_organo WHERE state = 'IN_PROGRESS'";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      resultSet.next();
      return resultSet.getInt(1);
    } catch (SQLException e) {
      throw new IllegalStateException("could not read the run's coverage", e);
    }
  }

  private static void assertRunState(String state) {
    Table runs = runTable();
    assertThat(runs).hasNumberOfRows(1);
    assertThat(runs).row(0).value("state").isEqualTo(state);
    assertThat(runs).row(0).value("finished_at").isNotNull();
  }

  /** How one covered Órgano was settled: the state, the reason beside it, and what it counted. */
  private record Coverage(String state, String reason, int added) {}

  /** Pinned to the Órgano's own row rather than to a position, so the order cannot mislead. */
  private static void assertCoverage(OrganoId organoId, String state) throws SQLException {
    assertThat(coverageOf(organoId).state())
        .as("state of Órgano %s", organoId)
        .isEqualTo(state);
  }

  private static String reasonFor(OrganoId organoId) throws SQLException {
    return coverageOf(organoId).reason();
  }

  private static int addedFor(OrganoId organoId) throws SQLException {
    return coverageOf(organoId).added();
  }

  private static Coverage coverageOf(OrganoId organoId) throws SQLException {
    String sql = "SELECT state, failure_reason, added FROM import_run_organo WHERE organo_id = ?";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, organoId.value());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("No coverage row for Órgano %s".formatted(organoId));
        }
        return new Coverage(
            resultSet.getString("state"),
            resultSet.getString("failure_reason"),
            resultSet.getInt("added"));
      }
    }
  }

  private static Table contratoTable() {
    return table("contrato_menor", "source_id");
  }

  private static Table importStateTable() {
    return table("contrato_menor_import_state", "organo_id");
  }

  private static Table runTable() {
    return table("import_run", "id");
  }

  private static Table coverageTable() {
    return table("import_run_organo", "organo_id");
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

  private static OrganoId insertOrgano(String sourceKey, boolean active, boolean importable)
      throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, importable)"
            + " VALUES (uuidv7(), ?, ?, ?, ?) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, "Órgano %s".formatted(sourceKey));
      statement.setBoolean(3, active);
      statement.setBoolean(4, importable);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(resultSet.getObject("id", UUID.class));
      }
    }
  }

  private static void insertImportState(OrganoId organoId, String state) throws SQLException {
    execute(
        "INSERT INTO contrato_menor_import_state (organo_id, state, covered_through)"
            + " VALUES (?, ?, ?)",
        organoId.value(),
        state,
        Timestamp.from(T_ZERO));
  }

  private static void execute(String sql, Object... parameters) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      statement.executeUpdate();
    }
  }

  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
