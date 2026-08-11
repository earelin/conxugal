package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.ImportOutcome;
import gal.conxugal.domain.organo.OrganoSource;
import gal.conxugal.domain.organo.OrganoSourceEntry;
import gal.conxugal.domain.organo.OrganoSourceUnavailableException;
import gal.conxugal.domain.time.Clock;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.SoftAssertions;
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
 * The catalogue import on the durable, system-wide guard, against a real Postgres: a live run of
 * the <em>other</em> importer refuses it, and a run whose process died stops refusing it once it
 * has gone quiet for long enough.
 *
 * <p><strong>{@code transactional = false}</strong>, because the run record commits in
 * transactions of its own and an importer calls it with none in scope. The default would join
 * those writes into one the test rolled back, and the guard would appear to work for a reason
 * production does not have.
 *
 * <p>Time is moved rather than back-dated, so a run goes stale because the world moved on and not
 * because a test rewrote its row.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportOrganosGuardIntegrationTest implements TestPropertyProvider {

  private static final Instant STARTED_AT = Instant.parse("2026-08-07T09:00:00Z");
  private static final Duration BOUND = Duration.ofMinutes(15);
  private static final List<OrganoSourceEntry> SOURCE_LIST =
      List.of(new OrganoSourceEntry("nova", "Nova"));

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  private final AtomicReference<Instant> now = new AtomicReference<>(STARTED_AT);

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @MockBean(Clock.class)
  Clock clock() {
    return now::get;
  }

  @MockBean(OrganoSource.class)
  OrganoSource organoSourceMock() {
    return mock(OrganoSource.class);
  }

  @Inject
  ImportOrganos importOrganos;

  @Inject
  OrganoSource organoSource;

  @Inject
  ImportRunRepository importRuns;

  @BeforeEach
  void resetTheClock() {
    now.set(STARTED_AT);
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  // The guard is one guard across both importers, and it is read from the database rather than
  // from this process, so a contratos menores run holds it without this JVM knowing anything.
  @Test
  void catalogue_import_is_refused_while_the_other_importer_has_one_live() {
    assertThat(importRuns.claim(Importer.CONTRATOS_MENORES, List.of())).isPresent();
    when(organoSource.fetchAll()).thenReturn(SOURCE_LIST);

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.ALREADY_RUNNING);
    assertThat(organoTable()).hasNumberOfRows(0);
  }

  // The trigger arrives from inside the running import, which is the one moment the guard has to
  // be held rather than merely claimed and released.
  @Test
  void catalogue_import_holds_the_guard_until_it_completes_and_the_next_one_then_starts() {
    AtomicReference<ImportOutcome> arrivedMidRun = new AtomicReference<>();
    when(organoSource.fetchAll())
        .thenAnswer(
            invocation -> {
              arrivedMidRun.set(importOrganos.run());
              return SOURCE_LIST;
            })
        .thenReturn(SOURCE_LIST);

    ImportOutcome outcome = importOrganos.run();
    ImportOutcome afterItReleased = importOrganos.run();

    assertThat(arrivedMidRun.get().status()).isEqualTo(ImportOutcome.Status.ALREADY_RUNNING);
    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    assertThat(afterItReleased.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
  }

  // The catalogue import writes no per-Órgano rows: it reports one outcome for the whole
  // catalogue, so a run of it is a run that covers none.
  @Test
  void successful_catalogue_import_records_its_verdict_its_times_and_its_counts()
      throws Exception {
    insertOrgano("xa-almacenada", "Xa almacenada");
    insertOrgano("desaparece", "Desaparece");
    when(organoSource.fetchAll())
        .thenReturn(
            List.of(
                new OrganoSourceEntry("xa-almacenada", "Xa almacenada, renomeada"),
                new OrganoSourceEntry("nova", "Nova")));

    ImportOutcome outcome = importOrganos.run();

    // The deactivated count goes to whoever triggered and to no column: the record holds what the
    // guard and the outcome read, and none of them reads how many Órganos an import stood down.
    assertThat(outcome.deactivated()).isEqualTo(1);
    Table runs = runTable();
    assertThat(runs).hasNumberOfRows(1);
    assertThat(runs).row(0).value("importer").isEqualTo("ORGANOS");
    assertThat(runs).row(0).value("state").isEqualTo("SUCCEEDED");
    assertThat(runs).row(0).value("added").isEqualTo(1);
    assertThat(runs).row(0).value("refreshed").isEqualTo(1);
    assertThat(coverageTable()).hasNumberOfRows(0);
    SoftAssertions.assertSoftly(softly -> {
      // Both stamps read the same pinned clock, so a run of this shape starts and finishes at one
      // instant; that both are written is what an outcome needs to report it.
      softly.assertThat(storedInstant("started_at")).isEqualTo(STARTED_AT);
      softly.assertThat(storedInstant("finished_at")).isEqualTo(STARTED_AT);
    });
  }

  @Test
  void catalogue_import_that_cannot_reach_the_source_is_recorded_as_failed() {
    when(organoSource.fetchAll()).thenThrow(new OrganoSourceUnavailableException("source is down"));

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.FAILURE);
    Table runs = runTable();
    assertThat(runs).hasNumberOfRows(1);
    assertThat(runs).row(0).value("state").isEqualTo("FAILED");
  }

  // Nothing sweeps a dead run's row, so the abandonment bound is the only thing that releases the
  // guard it left held — and the row itself is never rewritten to say it was abandoned.
  @Test
  void run_left_behind_by_dead_process_stops_refusing_the_catalogue_import_past_the_bound() {
    assertThat(importRuns.claim(Importer.CONTRATOS_MENORES, List.of())).isPresent();
    when(organoSource.fetchAll()).thenReturn(SOURCE_LIST);

    ImportOutcome refused = importOrganos.run();
    now.set(STARTED_AT.plus(BOUND).plusSeconds(1));
    ImportOutcome admitted = importOrganos.run();

    assertThat(refused.status()).isEqualTo(ImportOutcome.Status.ALREADY_RUNNING);
    assertThat(admitted.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    Table runs = runTable();
    assertThat(runs).hasNumberOfRows(2);
    assertThat(runs).row(0).value("state").isEqualTo("IN_PROGRESS");
    assertThat(runs).row(1).value("state").isEqualTo("SUCCEEDED");
  }

  private Table runTable() {
    return table("import_run", "started_at");
  }

  private Table coverageTable() {
    return table("import_run_organo", "organo_id");
  }

  private Table organoTable() {
    return table("organo_contratacion", "source_key");
  }

  // Off the container rather than the injected DataSource: with no ambient transaction both the
  // import and its record really commit, and these assertions are about what they committed.
  private static Table table(String name, String order) {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table(name)
        .columnsToOrder(new Table.Order[] {Table.Order.asc(order)})
        .build();
  }

  // Read through JDBC rather than asserted on the table, because AssertJ DB compares a timestamptz
  // against the session time zone and the claim here is about the instant.
  private static Instant storedInstant(String column) {
    try (Connection connection = rawConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT started_at, finished_at FROM import_run");
        ResultSet resultSet = statement.executeQuery()) {
      if (!resultSet.next()) {
        throw new IllegalStateException("No run is recorded");
      }
      return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    } catch (SQLException e) {
      throw new IllegalStateException("Could not read %s".formatted(column), e);
    }
  }

  private static void insertOrgano(String sourceKey, String name) throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active) "
            + "VALUES (uuidv7(), ?, ?, TRUE)";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, name);
      statement.executeUpdate();
    }
  }

  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
