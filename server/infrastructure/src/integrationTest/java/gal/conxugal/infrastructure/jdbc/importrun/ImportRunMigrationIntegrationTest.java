package gal.conxugal.infrastructure.jdbc.importrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the run schema directly rather than through the adapter, because most of the claims are
 * about what the two tables deliberately do <em>not</em> carry. A repository call cannot show the
 * absence of a column, and the absent ones here are load-bearing: no trigger and no scope, because
 * nothing this feature meets reads them, and no partial unique index, because the guard is a lock
 * inside the claim rather than a constraint on the table.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportRunMigrationIntegrationTest implements TestPropertyProvider {

  private static final Instant STARTED_AT = Instant.parse("2026-08-07T09:00:00Z");

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  // No trigger and no scope column: nothing this feature meets distinguishes a mark-triggered run
  // from an administrator-triggered one, and the covered Órganos below are the scope.
  @Test
  void the_run_holds_its_identity_times_state_and_counts_and_nothing_else() throws Exception {
    assertThat(columnNamesOf("import_run"))
        .containsExactlyInAnyOrder(
            "id",
            "importer",
            "state",
            "started_at",
            "finished_at",
            "last_advanced_at",
            "added",
            "refreshed");
  }

  @Test
  void the_coverage_row_holds_one_familys_own_state_counts_and_reason() throws Exception {
    assertThat(columnNamesOf("import_run_organo"))
        .containsExactlyInAnyOrder(
            "run_id", "organo_id", "family", "state", "added", "refreshed", "failure_reason");
  }

  // The guarantee a partial unique index would have given is asserted in the claim instead, so
  // there must be no index here beyond the two keys pretending to give it.
  @Test
  void nothing_beyond_the_primary_key_is_indexed_on_the_run() throws Exception {
    assertThat(indexNamesOf("import_run")).containsExactly("import_run_pkey");
  }

  @Test
  void the_coverage_row_reaches_both_the_run_and_the_organo_catalogue() throws Exception {
    assertThat(foreignKeyTargetsOf("import_run_organo"))
        .containsExactlyInAnyOrder("import_run", "organo_contratacion");
  }

  @Test
  void the_run_itself_reaches_no_other_table() throws Exception {
    assertThat(foreignKeyTargetsOf("import_run")).isEmpty();
  }

  // R27's requirement in the schema: marking an Órgano imports both families within one run, so
  // the key admits it once per family rather than once outright.
  @Test
  void both_families_of_the_same_organo_are_covered_within_one_run() throws Exception {
    UUID runId = insertRun("AMBAS_FAMILIAS");
    UUID organoId = insertOrgano("consorcio-x");

    insertCoverage(runId, organoId, "CONTRATOS_MENORES");
    insertCoverage(runId, organoId, "LICITACIONS");

    assertThat(familiesCovered(runId)).containsExactly("CONTRATOS_MENORES", "LICITACIONS");
  }

  @Test
  void second_coverage_row_for_the_same_organo_and_family_within_one_run_is_refused()
      throws Exception {
    UUID runId = insertRun("CONTRATOS_MENORES");
    UUID organoId = insertOrgano("consorcio-x");
    insertCoverage(runId, organoId);

    assertThatThrownBy(() -> insertCoverage(runId, organoId))
        .isInstanceOfSatisfying(SQLException.class, exception -> {
          // SQLSTATE 23505 is unique_violation.
          assertThat(exception.getSQLState()).isEqualTo("23505");
          assertThat(exception.getMessage()).contains("import_run_organo_pkey");
        });
  }

  @Test
  void coverage_of_an_unknown_run_is_refused_by_the_foreign_key() throws Exception {
    UUID organoId = insertOrgano("consorcio-x");

    assertThatThrownBy(() -> insertCoverage(UUID.randomUUID(), organoId))
        .isInstanceOfSatisfying(SQLException.class, exception -> {
          // SQLSTATE 23503 is foreign_key_violation.
          assertThat(exception.getSQLState()).isEqualTo("23503");
          assertThat(exception.getMessage()).contains("import_run_organo_run_id_fkey");
        });
  }

  @Test
  void coverage_of_an_unknown_organo_is_refused_by_the_foreign_key() throws Exception {
    UUID runId = insertRun("CONTRATOS_MENORES");

    assertThatThrownBy(() -> insertCoverage(runId, UUID.randomUUID()))
        .isInstanceOfSatisfying(SQLException.class, exception -> {
          assertThat(exception.getSQLState()).isEqualTo("23503");
          assertThat(exception.getMessage()).contains("import_run_organo_organo_id_fkey");
        });
  }

  // A run with no last-advanced time is a run the guard cannot tell alive from dead.
  @Test
  void run_without_the_instant_it_last_advanced_is_refused() {
    assertThatThrownBy(() -> insertRunLastAdvancedAt("CONTRATOS_MENORES", null))
        .isInstanceOfSatisfying(SQLException.class, exception ->
            // SQLSTATE 23502 is not_null_violation.
            assertThat(exception.getSQLState()).isEqualTo("23502"));
  }

  @Test
  void claimed_run_and_its_coverage_start_counting_nothing() throws Exception {
    UUID runId = insertRun("CONTRATOS_MENORES");
    UUID organoId = insertOrgano("consorcio-x");
    insertCoverage(runId, organoId);

    assertThat(runCounts(runId)).containsExactly(0, 0);
    assertThat(coverageCounts(runId)).containsExactly(0, 0);
  }

  private List<String> columnNamesOf(String table) throws SQLException {
    return queryStrings(
        "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
        table,
        "column_name");
  }

  private List<String> indexNamesOf(String table) throws SQLException {
    return queryStrings(
        "SELECT indexname FROM pg_indexes WHERE tablename = ?", table, "indexname");
  }

  private List<String> foreignKeyTargetsOf(String table) throws SQLException {
    return queryStrings(
        """
        SELECT target.relname AS target_table
          FROM pg_constraint constraint_
          JOIN pg_class source ON source.oid = constraint_.conrelid
          JOIN pg_class target ON target.oid = constraint_.confrelid
         WHERE source.relname = ?
           AND constraint_.contype = 'f'
        """,
        table,
        "target_table");
  }

  private List<String> familiesCovered(UUID runId) throws SQLException {
    return queryStrings(
        "SELECT family FROM import_run_organo WHERE run_id = ? ORDER BY family", runId, "family");
  }

  private List<Integer> runCounts(UUID runId) throws SQLException {
    return counts("SELECT added, refreshed FROM import_run WHERE id = ?", runId);
  }

  private List<Integer> coverageCounts(UUID runId) throws SQLException {
    return counts("SELECT added, refreshed FROM import_run_organo WHERE run_id = ?", runId);
  }

  private List<Integer> counts(String sql, UUID key) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, key);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("No row stored under %s".formatted(key));
        }
        return List.of(resultSet.getInt("added"), resultSet.getInt("refreshed"));
      }
    }
  }

  private List<String> queryStrings(String sql, Object parameter, String column)
      throws SQLException {
    List<String> values = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, parameter);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          values.add(resultSet.getString(column));
        }
      }
    }
    return values;
  }

  private UUID insertRun(String importer) throws SQLException {
    return insertRunLastAdvancedAt(importer, STARTED_AT);
  }

  private UUID insertRunLastAdvancedAt(String importer, @Nullable Instant lastAdvancedAt)
      throws SQLException {
    String sql =
        "INSERT INTO import_run (importer, state, started_at, last_advanced_at)"
            + " VALUES (?, 'IN_PROGRESS', ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, importer);
      statement.setTimestamp(2, Timestamp.from(STARTED_AT));
      statement.setObject(3, lastAdvancedAt == null ? null : Timestamp.from(lastAdvancedAt));
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        UUID id = resultSet.getObject("id", UUID.class);
        connection.commit();
        return id;
      }
    } catch (SQLException e) {
      rollbackQuietly(e);
      throw e;
    }
  }

  private void insertCoverage(UUID runId, UUID organoId) throws SQLException {
    insertCoverage(runId, organoId, "CONTRATOS_MENORES");
  }

  private void insertCoverage(UUID runId, UUID organoId, String family) throws SQLException {
    executeUpdate(
        "INSERT INTO import_run_organo (run_id, organo_id, family, state)"
            + " VALUES (?, ?, ?, 'PENDING')",
        runId, organoId, family);
  }

  private UUID insertOrgano(String sourceKey) throws SQLException {
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
        UUID id = resultSet.getObject("id", UUID.class);
        connection.commit();
        return id;
      }
    } catch (SQLException e) {
      rollbackQuietly(e);
      throw e;
    }
  }

  private void executeUpdate(String sql, Object... parameters) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      statement.executeUpdate();
      connection.commit();
    } catch (SQLException e) {
      rollbackQuietly(e);
      throw e;
    }
  }

  // The injected DataSource is Micronaut Data's connection-context-aware proxy, so every call here
  // shares one underlying connection: a failed statement aborts that connection's transaction, and
  // it must be rolled back before any later statement can run on it.
  private void rollbackQuietly(SQLException cause) {
    try (Connection connection = dataSource.getConnection()) {
      connection.rollback();
    } catch (SQLException rollbackException) {
      cause.addSuppressed(rollbackException);
    }
  }
}
