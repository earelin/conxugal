package gal.conxugal.infrastructure.jdbc.contrato;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies V13's schema directly rather than through the adapter, because the claims are about
 * what the table refuses and what it does not hold — neither of which any repository call can
 * show. The duplicate case in particular has to bypass the upsert: the upsert's whole job is to
 * absorb a repeated source identifier, so only a direct insert can reach the constraint that
 * makes the rule hold when use-case logic slips.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratoMenorMigrationIntegrationTest implements TestPropertyProvider {

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

  @Test
  void unique_constraint_rejects_direct_insert_of_an_already_stored_source_id()
      throws Exception {
    UUID organoId = insertOrgano("consorcio-x");
    insertContrato(4711L, organoId);

    assertThatThrownBy(() -> insertContrato(4711L, organoId))
        .isInstanceOfSatisfying(SQLException.class, exception -> {
          // SQLSTATE 23505 is unique_violation; pinning the constraint name too rules out a
          // different constraint failing for the wrong reason.
          assertThat(exception.getSQLState()).isEqualTo("23505");
          assertThat(exception.getMessage()).contains("contrato_menor_source_id_key");
        });
  }

  @Test
  void the_same_source_id_under_another_organo_is_still_duplicate() throws Exception {
    UUID firstOrgano = insertOrgano("consorcio-x");
    UUID secondOrgano = insertOrgano("axencia-y");
    insertContrato(4711L, firstOrgano);

    assertThatThrownBy(() -> insertContrato(4711L, secondOrgano))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> assertThat(exception.getSQLState()).isEqualTo("23505"));
  }

  // The awardee is one foreign key and nothing else: a name or fiscal-identifier column here
  // would be the normalised schema quietly denormalised, with a million rows repeating what
  // operador_economico holds once.
  @Test
  void the_table_holds_the_awardee_as_one_foreign_key_and_no_published_awardee_values()
      throws Exception {
    assertThat(columnNames())
        .containsExactlyInAnyOrder(
            "id",
            "source_id",
            "organo_id",
            "publication_date",
            "obxecto",
            "amount",
            "duration",
            "operador_economico_id");
  }

  @Test
  void an_unknown_organo_is_refused_by_the_foreign_key() {
    assertThatThrownBy(() -> insertContrato(4711L, UUID.randomUUID()))
        .isInstanceOfSatisfying(SQLException.class, exception -> {
          // SQLSTATE 23503 is foreign_key_violation.
          assertThat(exception.getSQLState()).isEqualTo("23503");
          assertThat(exception.getMessage()).contains("contrato_menor_organo_id_fkey");
        });
  }

  @Test
  void an_unknown_operador_is_refused_by_the_foreign_key() throws Exception {
    UUID organoId = insertOrgano("consorcio-x");

    assertThatThrownBy(() -> insertContratoAwardedTo(4711L, organoId, UUID.randomUUID()))
        .isInstanceOfSatisfying(SQLException.class, exception -> {
          assertThat(exception.getSQLState()).isEqualTo("23503");
          assertThat(exception.getMessage())
              .contains("contrato_menor_operador_economico_id_fkey");
        });
  }

  // The column bounds nothing, so a duration longer than the adapter's cap is stored rather
  // than refused. Capping is the adapter's job precisely so an unexpectedly long value loses
  // its tail instead of failing a batch and rejecting a real award; a bound here could only
  // reintroduce that failure.
  @Test
  void duration_past_the_adapters_cap_is_stored_rather_than_refused() throws Exception {
    UUID organoId = insertOrgano("consorcio-x");
    String duration = "d".repeat(500);

    insertContratoLasting(4711L, organoId, duration);

    assertThat(storedDuration(4711L)).isEqualTo(duration);
  }

  @Test
  void both_indexes_the_browsing_reads_will_need_are_created_with_the_table() throws Exception {
    assertThat(indexNames())
        .contains(
            "contrato_menor_operador_economico_id_idx",
            "contrato_menor_organo_id_publication_date_idx");
  }

  private List<String> columnNames() throws SQLException {
    return queryStrings(
        "SELECT column_name FROM information_schema.columns"
            + " WHERE table_name = 'contrato_menor'",
        "column_name");
  }

  private List<String> indexNames() throws SQLException {
    return queryStrings(
        "SELECT indexname FROM pg_indexes WHERE tablename = 'contrato_menor'", "indexname");
  }

  private String storedDuration(long sourceId) throws SQLException {
    String sql = "SELECT duration FROM contrato_menor WHERE source_id = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, sourceId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException(
              "No contract stored under source id %d".formatted(sourceId));
        }
        return resultSet.getString("duration");
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

  private void insertContrato(long sourceId, UUID organoId) throws SQLException {
    insertContratoAwardedTo(sourceId, organoId, null);
  }

  private void insertContratoLasting(long sourceId, UUID organoId, String duration)
      throws SQLException {
    executeUpdate(
        "INSERT INTO contrato_menor (source_id, organo_id, duration) VALUES (?, ?, ?)",
        sourceId, organoId, duration);
  }

  private void insertContratoAwardedTo(long sourceId, UUID organoId, UUID operadorId)
      throws SQLException {
    executeUpdate(
        "INSERT INTO contrato_menor (source_id, organo_id, operador_economico_id)"
            + " VALUES (?, ?, ?)",
        sourceId, organoId, operadorId);
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
