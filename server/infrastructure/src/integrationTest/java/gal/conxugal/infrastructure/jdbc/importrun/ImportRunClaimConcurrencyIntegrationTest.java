package gal.conxugal.infrastructure.jdbc.importrun;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
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
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The race the guard exists for: two triggers claiming at once must yield one run, with the loser
 * waiting on the lock and then finding the winner's row rather than passing the same check.
 *
 * <p>The first test holds the guard from outside before either claim starts, so the outcome does
 * not depend on how the two threads happen to interleave. A barrier alone would not do: it lets
 * one claim finish before the other begins, and would pass just as happily against an
 * implementation that takes no lock at all. Waiting until both claims are <em>blocked on the
 * advisory lock</em> — read out of {@code pg_locks} — is what makes this test mean what it says.
 *
 * <p>Everything the workers must see is written on a raw connection off the container, and their
 * results are read back the same way: the injected {@code DataSource} is Micronaut Data's
 * connection-context-aware proxy, bound to the test thread and its own transaction, and it can
 * neither publish a fixture to another thread nor see what one committed.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportRunClaimConcurrencyIntegrationTest implements TestPropertyProvider {

  private static final Instant CLAIMED_AT = Instant.parse("2026-08-07T09:00:00Z");
  private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
  private static final Duration POLL_DEADLINE = Duration.ofSeconds(30);

  private static final String BLOCKED_ON_AN_ADVISORY_LOCK =
      "SELECT count(*) AS answer FROM pg_locks WHERE locktype = 'advisory' AND NOT granted";

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

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
    return () -> CLAIMED_AT;
  }

  @Inject
  ImportRunRepository importRunRepository;

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  @Test
  void two_claims_released_onto_the_guard_together_yield_exactly_one_run() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    List<ImportRunId> claimed;
    try (Connection guardHolder = rawConnection()) {
      guardHolder.setAutoCommit(false);
      takeTheGuard(guardHolder);

      try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        final List<Future<Optional<ImportRunId>>> claims =
            List.of(
                executor.submit(() -> importRunRepository.claim(
                    Importer.CONTRATOS_MENORES, List.of(organoId))),
                executor.submit(() -> importRunRepository.claim(Importer.ORGANOS, List.of())));

        awaitBothBlockedOnTheGuard();
        guardHolder.rollback();

        claimed = new ArrayList<>();
        for (Future<Optional<ImportRunId>> claim : claims) {
          claim.get(POLL_DEADLINE.toSeconds(), TimeUnit.SECONDS).ifPresent(claimed::add);
        }
      }
    }

    assertThat(claimed).hasSize(1);
    assertThat(countRuns()).isEqualTo(1);
  }

  // The same race without the guard pre-held: non-deterministic on its own, but it exercises the
  // real timing rather than a staged one, and it costs almost nothing to run alongside.
  @Test
  void two_claims_racing_at_the_same_moment_yield_exactly_one_run() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    CyclicBarrier startTogether = new CyclicBarrier(2);

    List<ImportRunId> claimed = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<Optional<ImportRunId>>> claims =
          List.of(
              executor.submit(() -> claimOnceReleased(
                  startTogether, Importer.CONTRATOS_MENORES, List.of(organoId))),
              executor.submit(() -> claimOnceReleased(
                  startTogether, Importer.ORGANOS, List.of())));

      for (Future<Optional<ImportRunId>> claim : claims) {
        claim.get(POLL_DEADLINE.toSeconds(), TimeUnit.SECONDS).ifPresent(claimed::add);
      }
    }

    assertThat(claimed).hasSize(1);
    assertThat(countRuns()).isEqualTo(1);
  }

  private Optional<ImportRunId> claimOnceReleased(
      CyclicBarrier startTogether, Importer importer, List<OrganoId> covered) throws Exception {
    startTogether.await(POLL_DEADLINE.toSeconds(), TimeUnit.SECONDS);
    return importRunRepository.claim(importer, covered);
  }

  private static void takeTheGuard(Connection connection) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
      statement.setLong(1, JdbcImportRunRepository.IMPORT_GUARD_LOCK_KEY);
      statement.execute();
    }
  }

  /**
   * Waits until both claims are queued behind the advisory lock rather than merely running. This
   * is the assertion that the loser waits on the lock: an implementation that checked and inserted
   * without one would never appear here, and the wait would time out.
   *
   * <p>It does not filter on the key — a {@code bigint} advisory key is split across
   * {@code classid} and {@code objid} in {@code pg_locks}, and nothing else in this database takes
   * an advisory lock.
   */
  private void awaitBothBlockedOnTheGuard() throws Exception {
    Instant deadline = Instant.now().plus(POLL_DEADLINE);
    while (Instant.now().isBefore(deadline)) {
      if (countWaitingOnAnAdvisoryLock() == 2) {
        return;
      }
      Thread.sleep(POLL_INTERVAL.toMillis());
    }
    throw new AssertionError("Both claims should have queued behind the advisory lock");
  }

  private long countWaitingOnAnAdvisoryLock() throws SQLException {
    return querySingleCount(BLOCKED_ON_AN_ADVISORY_LOCK);
  }

  private long countRuns() throws SQLException {
    return querySingleCount("SELECT count(*) AS answer FROM import_run");
  }

  private long querySingleCount(String sql) throws SQLException {
    try (Connection connection = rawConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      if (!resultSet.next()) {
        throw new IllegalStateException("A count always answers with a row");
      }
      return resultSet.getLong("answer");
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
