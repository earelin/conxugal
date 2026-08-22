package gal.conxugal.infrastructure.jdbc.importrun;

import gal.conxugal.domain.importrun.ContractFamily;
import gal.conxugal.domain.importrun.CoveredOrgano;
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
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import java.sql.Array;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores import runs and serialises the guard that admits one of them at a time.
 *
 * <p><strong>The claim is one act.</strong> It takes a transaction-scoped advisory lock on a key
 * both importers share, looks for a run that is in progress <em>and still advancing</em>, and
 * either refuses or inserts — all inside one transaction, so the lock releases on commit. The
 * check and the write cannot be two acts: two triggers would both find no live run and both start.
 *
 * <p>It asks for read-committed isolation rather than assuming it: the loser's transaction begins
 * before the winner commits and sees the winner's row only because each statement takes a fresh
 * snapshot, so under repeatable read the guard would let a second import start.
 *
 * <p><strong>Every method starts a transaction of its own</strong>, rather than joining a caller's.
 * A claim that joined one would return the identity of a row the caller could still roll back —
 * leaving an importer that believes it holds the guard while nothing records it — and would hold a
 * system-wide lock for as long as the caller's work took. An advance that joined one would tie the
 * run record to the transaction writing contracts, which is the coupling the decision behind this
 * table rules out: progress has to be visible before a batch commits, and a failed progress write
 * must not roll imported contracts back.
 *
 * <p>The guarantee a partial unique index would have given is asserted rather than enforced, so
 * {@link #insert} is deliberately package-private and {@link #claim} is the only caller — a second
 * insertion path would step past the lock with no error to show for it.
 *
 * <p>The coverage rows are written and read by hand rather than mapped, because advancing one adds
 * to its counts rather than replacing them, and no derived method expresses an increment. Their
 * Órganos and families travel as two parallel arrays through {@code unnest}, so the statement is a
 * constant whatever the coverage's size rather than a string assembled around a placeholder count.
 *
 * <p>A write that matches no row is logged and let go rather than thrown. The record is evidence
 * about an import, not a participant in it, and failing the import because its bookkeeping missed
 * would be the wrong trade — but drifting silently would be worse, because the run's counts would
 * stop adding up with nothing to say why.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcImportRunRepository
    implements ImportRunRepository, GenericRepository<ImportRun, ImportRunId> {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcImportRunRepository.class);

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

  // The two arrays are unnested side by side, so one (Órgano, family) pair per row and the
  // statement stays a constant whatever the coverage's size.
  private static final String ENUMERATE_COVERAGE =
      """
      INSERT INTO import_run_organo (run_id, organo_id, family, state)
      SELECT ?::uuid, covered.organo_id, covered.family, ?::text
        FROM unnest(?::uuid[], ?::text[]) AS covered(organo_id, family)
      """;

  private static final String ADVANCE_RUN =
      """
      UPDATE import_run
         SET last_advanced_at = ?, added = added + ?, refreshed = refreshed + ?
       WHERE id = ?
      """;

  // Only an Órgano the run has not settled advances. Without the state filter a batch arriving
  // after a failure would set the row back to in progress while its reason stayed behind, turning
  // an Órgano the outcome must report as failed into one that merely looks busy.
  private static final String ADVANCE_ORGANO =
      """
      UPDATE import_run_organo
         SET state = ?, added = added + ?, refreshed = refreshed + ?
       WHERE run_id = ? AND organo_id = ? AND family = ? AND state = ANY(?::text[])
      """;

  /** The states an Órgano can still be advanced out of; every other one is its last. */
  private static final List<ImportRunOrganoState> UNSETTLED =
      List.of(ImportRunOrganoState.PENDING, ImportRunOrganoState.IN_PROGRESS);

  private static final String FINISH_ORGANO =
      """
      UPDATE import_run_organo
         SET state = ?, failure_reason = ?
       WHERE run_id = ? AND organo_id = ? AND family = ?
      """;

  // last_advanced_at moves with the verdict so it is never left older than finished_at. It has no
  // say over a completed run — a stored verdict reads back unchanged however long ago it landed —
  // but a record whose two timestamps disagree about the same moment invites a reader to believe
  // the wrong one.
  private static final String COMPLETE_RUN =
      """
      UPDATE import_run
         SET state = ?, finished_at = ?, last_advanced_at = ?,
             added = added + ?, refreshed = refreshed + ?
       WHERE id = ?
      """;

  private static final String COVERAGE_OF_RUN =
      """
      SELECT organo_id, family, state, added, refreshed, failure_reason
        FROM import_run_organo
       WHERE run_id = ?
       ORDER BY organo_id, family
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
  @Transactional(
      propagation = TransactionDefinition.Propagation.REQUIRES_NEW,
      isolation = TransactionDefinition.Isolation.READ_COMMITTED)
  public Optional<ImportRunId> claim(Importer importer, Collection<CoveredOrgano> coveredOrganos) {
    Objects.requireNonNull(importer, "importer must not be null");
    Objects.requireNonNull(coveredOrganos, "coveredOrganos must not be null");
    // A Set, because naming a pair twice is a caller's slip rather than a run that covers it
    // twice, and the composite key would answer it by rolling the whole claim back.
    Set<CoveredOrgano> covered = new LinkedHashSet<>(coveredOrganos);
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

  /**
   * The Órgano's row moves first, and the run's counts only follow it. Bumping the run for a batch
   * no covered Órgano accepted would leave the run's totals larger than the sum of the rows they
   * are supposed to total, with nothing in the record to say which.
   */
  @Override
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public void advance(
      ImportRunId runId, OrganoId organoId, ContractFamily family, int added, int refreshed) {
    int organosAdvanced = executeUpdate(ADVANCE_ORGANO, statement -> {
      statement.setString(1, ImportRunOrganoState.IN_PROGRESS.name());
      statement.setInt(2, added);
      statement.setInt(3, refreshed);
      statement.setObject(4, runId.value());
      statement.setObject(5, organoId.value());
      statement.setString(6, family.name());
      statement.setArray(7, namesOf(statement.getConnection(), UNSETTLED));
    });
    if (organosAdvanced == 0) {
      LOG.warn(
          "Run {} advanced against the {} of Órgano {}, which it does not cover or has already"
              + " settled; the batch is not counted",
          runId, family, organoId);
      return;
    }
    Instant now = clock.instant();
    executeUpdate(ADVANCE_RUN, statement -> {
      statement.setObject(1, atUtc(now));
      statement.setInt(2, added);
      statement.setInt(3, refreshed);
      statement.setObject(4, runId.value());
    });
  }

  @Override
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public void finishOrgano(
      ImportRunId runId,
      OrganoId organoId,
      ContractFamily family,
      ImportRunOrganoState state,
      @Nullable String failureReason) {
    int settled = executeUpdate(FINISH_ORGANO, statement -> {
      statement.setString(1, state.name());
      statement.setString(2, failureReason);
      statement.setObject(3, runId.value());
      statement.setObject(4, organoId.value());
      statement.setString(5, family.name());
    });
    if (settled == 0) {
      LOG.warn(
          "Run {} settled the {} of Órgano {}, which it does not cover", runId, family, organoId);
    }
  }

  @Override
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public void complete(
      ImportRunId runId,
      ImportRunState verdict,
      int addedSinceLastAdvance,
      int refreshedSinceLastAdvance) {
    String storable = verdict.requireStorableVerdict().name();
    OffsetDateTime now = atUtc(clock.instant());
    int completed = executeUpdate(COMPLETE_RUN, statement -> {
      statement.setString(1, storable);
      statement.setObject(2, now);
      statement.setObject(3, now);
      statement.setInt(4, addedSinceLastAdvance);
      statement.setInt(5, refreshedSinceLastAdvance);
      statement.setObject(6, runId.value());
    });
    if (completed == 0) {
      LOG.warn("Run {} was completed but is not recorded", runId);
    }
  }

  /**
   * The run's own row and nothing else — no coverage, because this is asked once per batch for as
   * long as an import runs. The abandonment bound is applied by the state itself, the same way
   * every other read of a run applies it.
   */
  @Override
  @Transactional
  public boolean holdsGuard(ImportRunId runId) {
    Instant now = clock.instant();
    return findById(runId)
        .map(run -> run.state().asReadAt(run.lastAdvancedAt(), now, bound()))
        .filter(ImportRunState.IN_PROGRESS::equals)
        .isPresent();
  }

  @Override
  @Transactional
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

  /**
   * The one place a coverage row is written, called only by {@link #claim}. The re-keying that let
   * one run cover an Órgano twice is exactly what makes a second insertion path tempting — a
   * family added later, against a run already claimed — and that path would put a row under a
   * guard the claim is no longer inside.
   */
  private void enumerateCoverage(ImportRunId runId, Set<CoveredOrgano> covered) {
    if (covered.isEmpty()) {
      return;
    }
    executeUpdate(ENUMERATE_COVERAGE, statement -> {
      Connection connection = statement.getConnection();
      UUID[] organoIds =
          covered.stream().map(one -> one.organoId().value()).toArray(UUID[]::new);
      String[] families = covered.stream().map(one -> one.family().name()).toArray(String[]::new);
      statement.setObject(1, runId.value());
      statement.setString(2, ImportRunOrganoState.PENDING.name());
      statement.setArray(3, connection.createArrayOf("uuid", organoIds));
      statement.setArray(4, connection.createArrayOf("text", families));
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
          covered.add(coverageAt(rows));
        }
        return covered;
      }
    });
  }

  private static ImportRunOrganoCoverage coverageAt(ResultSet rows) throws SQLException {
    return new ImportRunOrganoCoverage(
        new OrganoId(rows.getObject("organo_id", UUID.class)),
        ContractFamily.valueOf(rows.getString("family")),
        ImportRunOrganoState.valueOf(rows.getString("state")),
        rows.getInt("added"),
        rows.getInt("refreshed"),
        rows.getString("failure_reason"));
  }

  private Duration bound() {
    return guard.abandonmentBound();
  }

  private int executeUpdate(String sql, Binding binding) {
    return jdbcOperations.prepareStatement(sql, statement -> {
      binding.bind(statement);
      return statement.executeUpdate();
    });
  }

  /** An offset date-time, so these writes do not route a {@code timestamptz} through a zone. */
  private static OffsetDateTime atUtc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Array namesOf(Connection connection, List<ImportRunOrganoState> states)
      throws SQLException {
    return connection.createArrayOf(
        "text", states.stream().map(ImportRunOrganoState::name).toArray(String[]::new));
  }

  private static Instant instantAt(ResultSet rows, String column) throws SQLException {
    return rows.getObject(column, OffsetDateTime.class).toInstant();
  }

  @FunctionalInterface
  private interface Binding {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
