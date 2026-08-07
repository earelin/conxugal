package gal.conxugal.infrastructure.jdbc.importrun;

import gal.conxugal.domain.importrun.ImportRun;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunReport;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import io.micronaut.data.annotation.Insert;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Stores import runs and serialises the guard that admits one of them at a time.
 *
 * <p><strong>The claim is one act.</strong> It takes a transaction-scoped advisory lock on a key
 * both importers share, looks for a run that is in progress <em>and still advancing</em>, and
 * either refuses or inserts — all inside one transaction, so the lock releases on commit. The
 * check and the write cannot be two acts: two triggers would both find no live run and both start.
 *
 * <p>It relies on read-committed isolation, PostgreSQL's default. The loser's transaction begins
 * before the winner commits and sees the winner's row only because each statement takes a fresh
 * snapshot; under repeatable read it would find nothing and start a second import.
 *
 * <p>The guarantee a partial unique index would have given is asserted rather than enforced, so
 * {@link #insert} is deliberately package-private and {@link #claim} is the only caller — a second
 * insertion path would step past the lock with no error to show for it.
 *
 * <p>The coverage rows are written and read by hand rather than mapped, because advancing one adds
 * to its counts rather than replacing them, and no derived method expresses an increment. They
 * travel as one array through {@code unnest}, so the statement is a constant whatever the number
 * of covered Órganos rather than a string assembled around a placeholder count.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcImportRunRepository
    implements ImportRunRepository, GenericRepository<ImportRun, ImportRunId> {

  /**
   * The one key every importer's claim takes, so the guard is system-wide rather than one guard
   * each. Advisory locks share a single namespace across the database, so the value only has to be
   * one nothing else here uses; it is fixed in code rather than configured because two deployments
   * disagreeing about it would each believe it held the guard. The bytes spell the application's
   * name, which is what makes it recognisable in {@code pg_locks}.
   */
  static final long IMPORT_GUARD_LOCK_KEY = 0x636F_6E78_7567_616CL;

  private static final String TAKE_THE_GUARD = "SELECT pg_advisory_xact_lock(?)";

  private static final String LATEST_ADVANCE_OF_A_RUNNING_IMPORT =
      """
      SELECT last_advanced_at
        FROM import_run
       WHERE state = ?
       ORDER BY last_advanced_at DESC
       LIMIT 1
      """;

  private static final String ENUMERATE_COVERAGE =
      """
      INSERT INTO import_run_organo (run_id, organo_id, state)
      SELECT ?::uuid, covered.organo_id, ?::text FROM unnest(?::uuid[]) AS covered(organo_id)
      """;

  private static final String ADVANCE_RUN =
      """
      UPDATE import_run
         SET last_advanced_at = ?, added = added + ?, refreshed = refreshed + ?
       WHERE id = ?
      """;

  private static final String ADVANCE_ORGANO =
      """
      UPDATE import_run_organo
         SET state = ?, added = added + ?, refreshed = refreshed + ?
       WHERE run_id = ? AND organo_id = ?
      """;

  private static final String FINISH_ORGANO =
      """
      UPDATE import_run_organo
         SET state = ?, failure_reason = ?
       WHERE run_id = ? AND organo_id = ?
      """;

  private static final String COMPLETE_RUN =
      """
      UPDATE import_run
         SET state = ?, finished_at = ?
       WHERE id = ?
      """;

  private static final String COVERAGE_OF_RUN =
      """
      SELECT organo_id, state, added, refreshed, failure_reason
        FROM import_run_organo
       WHERE run_id = ?
       ORDER BY organo_id
      """;

  private final JdbcOperations jdbcOperations;
  private final Clock clock;
  private final ImportGuardConfiguration guard;

  protected JdbcImportRunRepository(
      JdbcOperations jdbcOperations, Clock clock, ImportGuardConfiguration guard) {
    this.jdbcOperations = jdbcOperations;
    this.clock = clock;
    this.guard = guard;
  }

  @Override
  @Transactional
  public Optional<ImportRunId> claim(Importer importer, Collection<OrganoId> coveredOrganos) {
    Objects.requireNonNull(importer, "importer must not be null");
    List<OrganoId> covered = List.copyOf(coveredOrganos);
    takeTheGuard();
    Instant now = clock.instant();
    if (theGuardIsHeld(now)) {
      return Optional.empty();
    }
    ImportRun claimed = insert(ImportRun.claimedAt(importer, now));
    ImportRunId runId =
        Objects.requireNonNull(claimed.id(), "the database assigns a run its identity on insert");
    enumerateCoverage(runId, covered);
    return Optional.of(runId);
  }

  @Override
  @Transactional
  public void advance(ImportRunId runId, OrganoId organoId, int added, int refreshed) {
    Instant now = clock.instant();
    executeUpdate(ADVANCE_RUN, statement -> {
      statement.setObject(1, atUtc(now));
      statement.setInt(2, added);
      statement.setInt(3, refreshed);
      statement.setObject(4, runId.value());
    });
    executeUpdate(ADVANCE_ORGANO, statement -> {
      statement.setString(1, ImportRunOrganoState.IN_PROGRESS.name());
      statement.setInt(2, added);
      statement.setInt(3, refreshed);
      statement.setObject(4, runId.value());
      statement.setObject(5, organoId.value());
    });
  }

  @Override
  public void finishOrgano(
      ImportRunId runId,
      OrganoId organoId,
      ImportRunOrganoState state,
      @Nullable String failureReason) {
    executeUpdate(FINISH_ORGANO, statement -> {
      statement.setString(1, state.name());
      statement.setString(2, failureReason);
      statement.setObject(3, runId.value());
      statement.setObject(4, organoId.value());
    });
  }

  @Override
  public void complete(ImportRunId runId, ImportRunState verdict) {
    executeUpdate(COMPLETE_RUN, statement -> {
      statement.setString(1, verdict.name());
      statement.setObject(2, atUtc(clock.instant()));
      statement.setObject(3, runId.value());
    });
  }

  @Override
  public Optional<ImportRunReport> findRun(ImportRunId runId) {
    Instant now = clock.instant();
    return findById(runId).map(run -> reportOf(run, now));
  }

  /**
   * The one insertion path, called only by {@link #claim} and reachable only from this package.
   * Micronaut Data returns the key the database generated, which is what a claim answers with.
   */
  @Insert
  abstract ImportRun insert(ImportRun run);

  abstract Optional<ImportRun> findById(ImportRunId id);

  private void takeTheGuard() {
    jdbcOperations.prepareStatement(TAKE_THE_GUARD, statement -> {
      statement.setLong(1, IMPORT_GUARD_LOCK_KEY);
      return statement.execute();
    });
  }

  /**
   * Whether a run is still holding the guard. The {@code state} filter only narrows the rows that
   * could possibly be live; the abandonment bound is applied nowhere but in the state itself, so
   * this and every read of a run agree on what a run past it amounts to. The most recently
   * advanced in-progress run is enough: if that one has gone quiet, every older one has too.
   */
  private boolean theGuardIsHeld(Instant now) {
    Instant lastAdvancedAt =
        jdbcOperations.prepareStatement(LATEST_ADVANCE_OF_A_RUNNING_IMPORT, statement -> {
          statement.setString(1, ImportRunState.IN_PROGRESS.name());
          try (ResultSet rows = statement.executeQuery()) {
            return rows.next() ? instantAt(rows, "last_advanced_at") : null;
          }
        });
    return lastAdvancedAt != null
        && ImportRunState.IN_PROGRESS.asReadAt(lastAdvancedAt, now, bound())
            == ImportRunState.IN_PROGRESS;
  }

  private void enumerateCoverage(ImportRunId runId, List<OrganoId> covered) {
    if (covered.isEmpty()) {
      return;
    }
    executeUpdate(ENUMERATE_COVERAGE, statement -> {
      Connection connection = statement.getConnection();
      UUID[] organoIds = covered.stream().map(OrganoId::value).toArray(UUID[]::new);
      statement.setObject(1, runId.value());
      statement.setString(2, ImportRunOrganoState.PENDING.name());
      statement.setArray(3, connection.createArrayOf("uuid", organoIds));
    });
  }

  private ImportRunReport reportOf(ImportRun run, Instant now) {
    ImportRunId runId =
        Objects.requireNonNull(run.id(), "a stored run always carries its identity");
    return new ImportRunReport(
        runId,
        run.importer(),
        run.state().asReadAt(run.lastAdvancedAt(), now, bound()),
        run.startedAt(),
        run.finishedAt(),
        run.added(),
        run.refreshed(),
        coverageOf(runId));
  }

  private List<ImportRunOrganoCoverage> coverageOf(ImportRunId runId) {
    return jdbcOperations.prepareStatement(COVERAGE_OF_RUN, statement -> {
      statement.setObject(1, runId.value());
      try (ResultSet rows = statement.executeQuery()) {
        List<ImportRunOrganoCoverage> covered = new ArrayList<>();
        while (rows.next()) {
          covered.add(new ImportRunOrganoCoverage(
              new OrganoId(rows.getObject("organo_id", UUID.class)),
              ImportRunOrganoState.valueOf(rows.getString("state")),
              rows.getInt("added"),
              rows.getInt("refreshed"),
              rows.getString("failure_reason")));
        }
        return covered;
      }
    });
  }

  private Duration bound() {
    return guard.abandonmentBound();
  }

  private void executeUpdate(String sql, Binding binding) {
    jdbcOperations.prepareStatement(sql, statement -> {
      binding.bind(statement);
      return statement.executeUpdate();
    });
  }

  /** An offset date-time, so a {@code timestamptz} never routes through the JVM's default zone. */
  private static OffsetDateTime atUtc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instantAt(ResultSet rows, String column) throws SQLException {
    return rows.getObject(column, OffsetDateTime.class).toInstant();
  }

  @FunctionalInterface
  private interface Binding {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
