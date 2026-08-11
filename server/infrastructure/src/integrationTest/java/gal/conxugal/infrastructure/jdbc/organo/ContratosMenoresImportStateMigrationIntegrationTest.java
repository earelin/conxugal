package gal.conxugal.infrastructure.jdbc.organo;

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
import java.sql.Statement;
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
 * Verifies V14's schema directly rather than through the adapter, because the claims are about
 * what the table holds, what it refuses and — most of it — what it deliberately does <em>not</em>
 * carry. A repository call cannot show the absence of a column, and the absence of any reference
 * to a run is the property that keeps pruning run history from stranding a half-loaded Órgano.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratosMenoresImportStateMigrationIntegrationTest implements TestPropertyProvider {

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

  // The acceptance criterion the whole table shape exists for: nothing here names a run, so
  // nothing that prunes run history can reach a cursor or the covered-through instant.
  @Test
  void the_table_holds_the_three_organo_facts_and_nothing_naming_any_run() throws Exception {
    assertThat(columnNames())
        .containsExactlyInAnyOrder("organo_id", "state", "cursor_date", "covered_through");
  }

  @Test
  void no_foreign_key_reaches_any_table_other_than_the_organo_catalogue() throws Exception {
    assertThat(foreignKeyTargets()).containsExactly("organo_contratacion");
  }

  // Its identity is the Órgano's, so a second row for the same Órgano would be a second answer
  // to the same question.
  @Test
  void second_state_row_for_the_same_organo_is_refused() throws Exception {
    UUID organoId = insertOrgano("consorcio-x");
    insertState(organoId, "INCOMPLETE");

    assertThatThrownBy(() -> insertState(organoId, "COMPLETE"))
        .isInstanceOfSatisfying(SQLException.class, exception -> {
          // SQLSTATE 23505 is unique_violation.
          assertThat(exception.getSQLState()).isEqualTo("23505");
          assertThat(exception.getMessage()).contains("contrato_menor_import_state_pkey");
        });
  }

  @Test
  void an_unknown_organo_is_refused_by_the_foreign_key() {
    assertThatThrownBy(() -> insertState(UUID.randomUUID(), "INCOMPLETE"))
        .isInstanceOfSatisfying(SQLException.class, exception -> {
          // SQLSTATE 23503 is foreign_key_violation.
          assertThat(exception.getSQLState()).isEqualTo("23503");
          assertThat(exception.getMessage())
              .contains("contrato_menor_import_state_organo_id_fkey");
        });
  }

  // Every row that exists was created when an import started, and that is when T₀ is stamped —
  // so a row without one is not a state this system can interpret.
  @Test
  void state_row_without_the_instant_it_is_covered_through_is_refused() throws Exception {
    UUID organoId = insertOrgano("consorcio-x");

    assertThatThrownBy(() -> insertStateCoveredThrough(organoId, "INCOMPLETE", null))
        .isInstanceOfSatisfying(SQLException.class, exception ->
            // SQLSTATE 23502 is not_null_violation.
            assertThat(exception.getSQLState()).isEqualTo("23502"));
  }

  // The cursor is the one nullable column: an Órgano whose import has started but not yet walked
  // past its first window has nowhere to point it.
  @Test
  void started_import_may_hold_no_cursor_yet() throws Exception {
    UUID organoId = insertOrgano("consorcio-x");

    insertState(organoId, "INCOMPLETE");

    assertThat(storedCursorDate(organoId)).isNull();
  }

  private List<String> columnNames() throws SQLException {
    return queryStrings(
        "SELECT column_name FROM information_schema.columns"
            + " WHERE table_name = 'contrato_menor_import_state'",
        "column_name");
  }

  private List<String> foreignKeyTargets() throws SQLException {
    return queryStrings(
        """
        SELECT target.relname AS target_table
          FROM pg_constraint constraint_
          JOIN pg_class source ON source.oid = constraint_.conrelid
          JOIN pg_class target ON target.oid = constraint_.confrelid
         WHERE source.relname = 'contrato_menor_import_state'
           AND constraint_.contype = 'f'
        """,
        "target_table");
  }

  private @Nullable String storedCursorDate(UUID organoId)
      throws SQLException {
    String sql = "SELECT cursor_date FROM contrato_menor_import_state WHERE organo_id = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, organoId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("No state stored for Órgano %s".formatted(organoId));
        }
        return resultSet.getString("cursor_date");
      }
    }
  }

  private List<String> queryStrings(String sql, String column) throws SQLException {
    List<String> values = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      while (resultSet.next()) {
        values.add(resultSet.getString(column));
      }
    }
    return values;
  }

  private void insertState(UUID organoId, String state) throws SQLException {
    insertStateCoveredThrough(organoId, state, Instant.parse("2026-08-06T09:00:00Z"));
  }

  private void insertStateCoveredThrough(
      UUID organoId, String state, @Nullable Instant coveredThrough)
      throws SQLException {
    executeUpdate(
        "INSERT INTO contrato_menor_import_state (organo_id, state, covered_through)"
            + " VALUES (?, ?, ?)",
        organoId, state, coveredThrough == null ? null : Timestamp.from(coveredThrough));
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

  // The injected DataSource is Micronaut Data's connection-context-aware proxy, so every call
  // here shares one underlying connection: a failed statement aborts that connection's
  // transaction, and it must be rolled back before any later statement (including a following
  // test's @AfterEach TRUNCATE) can run on it.
  private void rollbackQuietly(SQLException cause) {
    try (Connection connection = dataSource.getConnection()) {
      connection.rollback();
    } catch (SQLException rollbackException) {
      cause.addSuppressed(rollbackException);
    }
  }
}
