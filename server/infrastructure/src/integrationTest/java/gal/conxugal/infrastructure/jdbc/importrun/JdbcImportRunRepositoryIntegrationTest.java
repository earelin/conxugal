package gal.conxugal.infrastructure.jdbc.importrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunReport;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.SoftAssertions;
import org.assertj.db.type.AssertDbConnection;
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
 * The guard, the derived-abandoned read and the run record, single-threaded. The race two claims
 * run is {@link ImportRunClaimConcurrencyIntegrationTest}'s.
 *
 * <p><strong>{@code transactional = false}</strong>, because every method of this adapter opens a
 * transaction of its own and an importer calls it with none in scope. The default would wrap each
 * test in a transaction that quietly supplied the connection each statement needs and joined the
 * writes into one — so the suite would pass for a reason production does not have, and a claim
 * that could only work inside somebody else's transaction would look correct. Fixtures and
 * assertions therefore go through raw connections off the container: what the adapter writes is
 * really committed.
 *
 * <p>Time is moved rather than back-dated: the clock the adapter reads is a mutable reference here,
 * so a run becomes stale because the world moved on, not because a test rewrote its row. That is
 * what makes "the stale row is untouched" provable rather than circular.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcImportRunRepositoryIntegrationTest implements TestPropertyProvider {

  private static final Instant CLAIMED_AT = Instant.parse("2026-08-07T09:00:00Z");
  private static final Duration BOUND = Duration.ofMinutes(15);

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  private final AtomicReference<Instant> now = new AtomicReference<>(CLAIMED_AT);

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
    return now::get;
  }

  @Inject
  ImportRunRepository importRunRepository;

  @BeforeEach
  void resetTheClock() {
    now.set(CLAIMED_AT);
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  @Test
  void claim_records_the_run_and_every_organo_it_covers_at_pending() throws Exception {
    OrganoId first = insertOrgano("consorcio-x");
    OrganoId second = insertOrgano("axencia-y");

    importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(first, second));

    Table runs = runTable();
    assertThat(runs).hasNumberOfRows(1);
    assertThat(runs).row(0).value("importer").isEqualTo("CONTRATOS_MENORES");
    assertThat(runs).row(0).value("state").isEqualTo("IN_PROGRESS");
    assertThat(runs).row(0).value("finished_at").isNull();
    assertThat(runs).row(0).value("added").isEqualTo(0);
    assertThat(runs).row(0).value("refreshed").isEqualTo(0);

    Table coverage = coverageTable();
    assertThat(coverage).hasNumberOfRows(2);
    assertThat(coverage).row(0).value("state").isEqualTo("PENDING");
    assertThat(coverage).row(1).value("state").isEqualTo("PENDING");
  }

  // Readable before any of them has been touched: a run that dies at the fortieth of four hundred
  // still has to be able to name the list it was going to cover.
  @Test
  void the_covered_organos_are_readable_straight_after_the_claim() throws Exception {
    OrganoId first = insertOrgano("consorcio-x");
    OrganoId second = insertOrgano("axencia-y");

    ImportRunId runId =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(first, second)).orElseThrow();

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    assertThat(report.coveredOrganos())
        .extracting(ImportRunOrganoCoverage::organoId, ImportRunOrganoCoverage::state)
        .containsExactlyInAnyOrder(
            tuple(first, ImportRunOrganoState.PENDING),
            tuple(second, ImportRunOrganoState.PENDING));
  }

  // The catalogue import's shape: it covers no Órgano, and it still holds the guard.
  @Test
  void claim_covering_no_organos_records_run_that_covers_none() {
    ImportRunId runId = importRunRepository.claim(Importer.ORGANOS, List.of()).orElseThrow();

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    assertThat(report.coveredOrganos()).isEmpty();
    assertThat(report.importer()).isEqualTo(Importer.ORGANOS);
  }

  // Naming one twice is a caller's slip, not a run that covers it twice — and the composite key
  // would answer it by rolling the whole claim back, losing the guard with it.
  @Test
  void claim_naming_the_same_organo_twice_covers_it_once() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, List.of(organoId, organoId))
            .orElseThrow();

    assertThat(importRunRepository.findRun(runId).orElseThrow().coveredOrganos()).hasSize(1);
  }

  @Test
  void second_claim_of_the_same_importer_while_run_advances_is_refused() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId));

    Optional<ImportRunId> refused =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId));

    assertThat(refused).isEmpty();
  }

  // The guard is system-wide, not one per importer: the catalogue import is held by a contratos
  // menores run and the other way round.
  @Test
  void claim_of_the_other_importer_while_run_advances_is_refused() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId));

    assertThat(importRunRepository.claim(Importer.ORGANOS, List.of())).isEmpty();
  }

  @Test
  void refused_claim_inserts_nothing() throws Exception {
    OrganoId held = insertOrgano("consorcio-x");
    OrganoId other = insertOrgano("axencia-y");
    importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(held));

    importRunRepository.claim(Importer.ORGANOS, List.of(other));

    assertThat(runTable()).hasNumberOfRows(1);
    assertThat(coverageTable()).hasNumberOfRows(1);
  }

  @Test
  void run_advanced_within_the_bound_still_holds_the_guard() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId)).orElseThrow();

    now.set(CLAIMED_AT.plus(BOUND).minusSeconds(60));
    importRunRepository.advance(runId, organoId, 100, 0);
    now.set(CLAIMED_AT.plus(BOUND).plusSeconds(60));

    assertThat(importRunRepository.claim(Importer.ORGANOS, List.of())).isEmpty();
  }

  // Without this rule one crash mid-run would hold every import in the system for good: nothing
  // sweeps the row and no administrative function clears it.
  @Test
  void run_that_has_not_advanced_within_the_bound_releases_the_guard() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId));

    now.set(CLAIMED_AT.plus(BOUND).plusSeconds(1));

    assertThat(importRunRepository.claim(Importer.ORGANOS, List.of())).isPresent();
  }

  @Test
  void stale_runs_row_and_its_coverage_are_left_exactly_as_they_stood() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    final ImportRunId stale =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId)).orElseThrow();

    now.set(CLAIMED_AT.plus(BOUND).plusSeconds(1));
    importRunRepository.claim(Importer.ORGANOS, List.of());

    // Ordered by start, so row 0 is the run that died and row 1 the one that took the guard from
    // it — both asserted, so an update that reached the wrong row could not pass.
    Table runs = runTable();
    assertThat(runs).hasNumberOfRows(2);
    assertThat(runs).row(1).value("importer").isEqualTo("ORGANOS");
    assertThat(runs).row(0).value("importer").isEqualTo("CONTRATOS_MENORES");
    assertThat(runs).row(0).value("state").isEqualTo("IN_PROGRESS");
    assertThat(runs).row(0).value("finished_at").isNull();
    assertThat(runs).row(0).value("added").isEqualTo(0);
    assertThat(runs).row(0).value("refreshed").isEqualTo(0);
    assertThat(storedStartedAt(stale)).isEqualTo(CLAIMED_AT);
    assertThat(storedLastAdvancedAt(stale)).isEqualTo(CLAIMED_AT);

    // The coverage row is what a mis-scoped update from the second claim would reach, and the
    // second claim covers no Órgano at all, so it must still read exactly as it was enumerated.
    Table coverage = coverageTable();
    assertThat(coverage).hasNumberOfRows(1);
    assertThat(coverage).row(0).value("state").isEqualTo("PENDING");
    assertThat(coverage).row(0).value("added").isEqualTo(0);
    assertThat(coverage).row(0).value("refreshed").isEqualTo(0);
    assertThat(coverage).row(0).value("failure_reason").isNull();
  }

  @Test
  void every_read_of_stale_run_reports_it_abandoned() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId stale =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId)).orElseThrow();

    now.set(CLAIMED_AT.plus(BOUND).plusSeconds(1));

    assertThat(importRunRepository.findRun(stale).orElseThrow().state())
        .isEqualTo(ImportRunState.ABANDONED);
  }

  @Test
  void advance_moves_the_runs_counts_and_the_covered_organos_own() throws Exception {
    OrganoId advanced = insertOrgano("consorcio-x");
    OrganoId untouched = insertOrgano("axencia-y");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, List.of(advanced, untouched))
            .orElseThrow();

    importRunRepository.advance(runId, advanced, 100, 4);
    importRunRepository.advance(runId, advanced, 60, 6);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.added()).isEqualTo(160);
      softly.assertThat(report.refreshed()).isEqualTo(10);
      softly.assertThat(coverageFor(report, advanced))
          .isEqualTo(new ImportRunOrganoCoverage(
              advanced, ImportRunOrganoState.IN_PROGRESS, 160, 10, null));
      softly.assertThat(coverageFor(report, untouched))
          .isEqualTo(new ImportRunOrganoCoverage(
              untouched, ImportRunOrganoState.PENDING, 0, 0, null));
    });
  }

  // The run's counts are the sum of its coverage rows', so a batch no covered Órgano accepted must
  // not reach them — otherwise the outcome reports contracts against nothing that imported them.
  @Test
  void advance_against_organo_the_run_does_not_cover_counts_nothing() throws Exception {
    OrganoId covered = insertOrgano("consorcio-x");
    OrganoId uncovered = insertOrgano("axencia-y");
    ImportRunId runId =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(covered)).orElseThrow();

    importRunRepository.advance(runId, uncovered, 100, 4);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.added()).isZero();
      softly.assertThat(report.refreshed()).isZero();
      softly.assertThat(report.coveredOrganos())
          .containsExactly(new ImportRunOrganoCoverage(
              covered, ImportRunOrganoState.PENDING, 0, 0, null));
    });
  }

  // A late batch must not un-fail an Órgano the outcome has to report as failed, nor leave it in
  // progress while its reason stays behind.
  @Test
  void advance_after_the_organo_has_been_settled_leaves_it_settled() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId)).orElseThrow();
    importRunRepository.finishOrgano(
        runId, organoId, ImportRunOrganoState.FAILED, "the source stopped answering");

    importRunRepository.advance(runId, organoId, 100, 4);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.added()).isZero();
      softly.assertThat(coverageFor(report, organoId))
          .isEqualTo(new ImportRunOrganoCoverage(
              organoId, ImportRunOrganoState.FAILED, 0, 0, "the source stopped answering"));
    });
  }

  @Test
  void finishing_an_organo_records_its_state_and_its_reason() throws Exception {
    OrganoId failed = insertOrgano("consorcio-x");
    OrganoId stopped = insertOrgano("axencia-y");
    ImportRunId runId =
        importRunRepository
            .claim(Importer.CONTRATOS_MENORES, List.of(failed, stopped))
            .orElseThrow();

    importRunRepository.finishOrgano(
        runId, failed, ImportRunOrganoState.FAILED, "the source stopped answering");
    importRunRepository.finishOrgano(runId, stopped, ImportRunOrganoState.STOPPED, null);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(coverageFor(report, failed))
          .isEqualTo(new ImportRunOrganoCoverage(
              failed, ImportRunOrganoState.FAILED, 0, 0, "the source stopped answering"));
      softly.assertThat(coverageFor(report, stopped))
          .isEqualTo(new ImportRunOrganoCoverage(
              stopped, ImportRunOrganoState.STOPPED, 0, 0, null));
    });
  }

  @Test
  void settling_organo_the_run_does_not_cover_records_nothing() throws Exception {
    OrganoId covered = insertOrgano("consorcio-x");
    OrganoId uncovered = insertOrgano("axencia-y");
    ImportRunId runId =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(covered)).orElseThrow();

    importRunRepository.finishOrgano(runId, uncovered, ImportRunOrganoState.FAILED, "nowhere");

    assertThat(coverageTable()).hasNumberOfRows(1);
    assertThat(importRunRepository.findRun(runId).orElseThrow().coveredOrganos())
        .containsExactly(
            new ImportRunOrganoCoverage(covered, ImportRunOrganoState.PENDING, 0, 0, null));
  }

  @Test
  void completing_run_records_its_verdict_its_finish_time_and_its_counts() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId)).orElseThrow();
    importRunRepository.advance(runId, organoId, 100, 4);
    importRunRepository.finishOrgano(runId, organoId, ImportRunOrganoState.SUCCEEDED, null);

    Instant finishedAt = CLAIMED_AT.plusSeconds(600);
    now.set(finishedAt);
    importRunRepository.complete(runId, ImportRunState.PARTIALLY_SUCCEEDED);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.state()).isEqualTo(ImportRunState.PARTIALLY_SUCCEEDED);
      softly.assertThat(report.finishedAt()).isEqualTo(finishedAt);
      softly.assertThat(report.startedAt()).isEqualTo(CLAIMED_AT);
      softly.assertThat(report.added()).isEqualTo(100);
      softly.assertThat(report.refreshed()).isEqualTo(4);
    });
  }

  // A run cannot be completed as abandoned: that state is derived on read and stored nowhere, and
  // a caller completing with a state it just read has one in hand.
  @Test
  void completing_run_as_abandoned_is_refused_and_writes_nothing() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId)).orElseThrow();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> importRunRepository.complete(runId, ImportRunState.ABANDONED));

    Table runs = runTable();
    assertThat(runs).row(0).value("state").isEqualTo("IN_PROGRESS");
    assertThat(runs).row(0).value("finished_at").isNull();
  }

  // Re-completing is a caller's mistake with no requirement attached; what matters is that it
  // rewrites nothing but the verdict it was given.
  @Test
  void completing_run_that_is_already_complete_leaves_its_counts_and_coverage_alone()
      throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId runId =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId)).orElseThrow();
    importRunRepository.advance(runId, organoId, 100, 4);
    importRunRepository.finishOrgano(runId, organoId, ImportRunOrganoState.SUCCEEDED, null);
    importRunRepository.complete(runId, ImportRunState.SUCCEEDED);

    importRunRepository.complete(runId, ImportRunState.FAILED);

    ImportRunReport report = importRunRepository.findRun(runId).orElseThrow();
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(report.state()).isEqualTo(ImportRunState.FAILED);
      softly.assertThat(report.added()).isEqualTo(100);
      softly.assertThat(coverageFor(report, organoId).state())
          .isEqualTo(ImportRunOrganoState.SUCCEEDED);
    });
  }

  @Test
  void completing_unknown_run_records_nothing() {
    importRunRepository.complete(new ImportRunId(UUID.randomUUID()), ImportRunState.SUCCEEDED);

    assertThat(runTable()).hasNumberOfRows(0);
  }

  // Every verdict releases the guard for good — a finished run cannot become abandoned, so the
  // next claim must not have to wait out the bound first.
  @Test
  void completed_run_holds_the_guard_no_longer_whatever_its_verdict() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ImportRunId succeeded =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of(organoId)).orElseThrow();
    importRunRepository.complete(succeeded, ImportRunState.SUCCEEDED);

    ImportRunId failed = importRunRepository.claim(Importer.ORGANOS, List.of()).orElseThrow();
    importRunRepository.complete(failed, ImportRunState.FAILED);

    ImportRunId partial =
        importRunRepository.claim(Importer.CONTRATOS_MENORES, List.of()).orElseThrow();
    importRunRepository.complete(partial, ImportRunState.PARTIALLY_SUCCEEDED);

    assertThat(importRunRepository.claim(Importer.ORGANOS, List.of())).isPresent();
  }

  @Test
  void read_of_an_unknown_run_is_empty() {
    assertThat(importRunRepository.findRun(new ImportRunId(UUID.randomUUID()))).isEmpty();
  }

  private static ImportRunOrganoCoverage coverageFor(ImportRunReport report, OrganoId organoId) {
    return report.coveredOrganos().stream()
        .filter(coverage -> coverage.organoId().equals(organoId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Órgano %s is not covered".formatted(organoId)));
  }

  private Table runTable() {
    return assertDb()
        .table("import_run")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("started_at")})
        .build();
  }

  private Table coverageTable() {
    return assertDb()
        .table("import_run_organo")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("organo_id")})
        .build();
  }

  // Off the container rather than the injected DataSource: with no ambient transaction the adapter
  // really commits, and these assertions are about what it committed.
  private static AssertDbConnection assertDb() {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create();
  }

  // Read through JDBC rather than asserted on the table, because AssertJ DB compares a timestamptz
  // against the session time zone and the claims here are about the instant.
  private Instant storedStartedAt(ImportRunId runId) throws SQLException {
    return storedInstant(runId, "SELECT started_at FROM import_run WHERE id = ?", "started_at");
  }

  private Instant storedLastAdvancedAt(ImportRunId runId) throws SQLException {
    return storedInstant(
        runId, "SELECT last_advanced_at FROM import_run WHERE id = ?", "last_advanced_at");
  }

  private Instant storedInstant(ImportRunId runId, String sql, String column) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, runId.value());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("No run stored under %s".formatted(runId));
        }
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
      }
    }
  }

  private OrganoId insertOrgano(String sourceKey) throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active)"
            + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id";
    try (Connection connection = rawConnection();
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

  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
