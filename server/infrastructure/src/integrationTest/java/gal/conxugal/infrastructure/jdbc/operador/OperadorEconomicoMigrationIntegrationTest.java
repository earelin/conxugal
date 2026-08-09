package gal.conxugal.infrastructure.jdbc.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The guarantees V12 makes at the store rather than in whichever caller remembered them: one
 * operador per canonical fiscal identifier, one retained name per operador and name, and a
 * retained name that cannot outlive the operador it belongs to.
 *
 * <p>Driven with raw SQL and committed, unlike the repository's own tests: a deliberately violated
 * constraint aborts the connection Micronaut Data shares with the adapter, so these statements have
 * to stand on their own and roll back before the next one runs on it.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperadorEconomicoMigrationIntegrationTest implements TestPropertyProvider {

  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, 3, 14);

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

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  // The catalogue splitting an operador in two is the failure that would undercount silently, so
  // it is refused here rather than left to whichever use case canonicalises first.
  @Test
  void unique_fiscal_id_refuses_second_operador_under_stored_identifier() throws Exception {
    insertOperador("B12345678", "Servizos Galegos SL");

    assertThatThrownBy(() -> insertOperador("B12345678", "Outro Nome SL"))
        .isInstanceOfSatisfying(SQLException.class, this::assertViolatesFiscalIdUniqueness);
    Table operadores = operadorTable();
    assertThat(operadores).hasNumberOfRows(1);
    assertThat(operadores).row(0).value("name").isEqualTo("Servizos Galegos SL");
  }

  // Only surrounding whitespace and letter case are ignored, and the column holds the reduced
  // form — so anything the reduction leaves in place is another operador and is accepted.
  @Test
  void unique_fiscal_id_accepts_an_identifier_differing_by_internal_spacing() throws Exception {
    insertOperador("B12345678", "Servizos Galegos SL");

    insertOperador("B1234 5678", "Outro Nome SL");

    assertThat(operadorTable()).hasNumberOfRows(2);
  }

  // Without the key the table grows one row per award instead of one per distinct name, and the
  // largest operador would hold tens of thousands of rows saying the same thing.
  @Test
  void primary_key_refuses_second_row_for_one_operador_and_name() throws Exception {
    UUID operadorId = insertOperador("B12345678", "Servizos Galegos SL");
    retainName(operadorId, "Servizos Galegos");

    assertThatThrownBy(() -> retainName(operadorId, "Servizos Galegos"))
        .isInstanceOfSatisfying(SQLException.class, this::assertViolatesRetainedNameKey);
    assertThat(nomeAlternativoTable()).hasNumberOfRows(1);
  }

  @Test
  void primary_key_accepts_the_same_name_retained_under_two_different_operadores()
      throws Exception {
    UUID first = insertOperador("B12345678", "Servizos Galegos SL");
    UUID second = insertOperador("B87654321", "Obras do Miño SL");
    retainName(first, "Grupo Galego");

    retainName(second, "Grupo Galego");

    assertThat(nomeAlternativoTable()).hasNumberOfRows(2);
  }

  // Nothing deletes an operador — the catalogue's only writer is the derivation — and the key is
  // what keeps a retained name from being left pointing at a row that is gone.
  @Test
  void foreign_key_refuses_deleting_operador_that_still_holds_retained_name() throws Exception {
    UUID operadorId = insertOperador("B12345678", "Servizos Galegos SL");
    retainName(operadorId, "Servizos Galegos");

    assertThatThrownBy(() -> deleteOperador(operadorId))
        .isInstanceOfSatisfying(SQLException.class, this::assertViolatesRetainedNameForeignKey);
    assertThat(operadorTable()).hasNumberOfRows(1);
    assertThat(nomeAlternativoTable()).hasNumberOfRows(1);
  }

  private void assertViolatesFiscalIdUniqueness(SQLException exception) {
    // SQLSTATE 23505 is unique_violation; pinning the constraint name too rules out a
    // coincidental different constraint failing for the wrong reason.
    assertThat(exception.getSQLState()).isEqualTo("23505");
    assertThat(exception.getMessage()).contains("operador_economico_fiscal_id_key");
  }

  private void assertViolatesRetainedNameKey(SQLException exception) {
    assertThat(exception.getSQLState()).isEqualTo("23505");
    assertThat(exception.getMessage()).contains("operador_economico_nome_alternativo_pkey");
  }

  private void assertViolatesRetainedNameForeignKey(SQLException exception) {
    // SQLSTATE 23503 is foreign_key_violation — the delete was refused rather than cascading.
    assertThat(exception.getSQLState()).isEqualTo("23503");
    assertThat(exception.getMessage())
        .contains("operador_economico_nome_alternativo_operador_fkey");
  }

  private Table operadorTable() {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("operador_economico")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("fiscal_id")})
        .build();
  }

  private Table nomeAlternativoTable() {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("operador_economico_nome_alternativo")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("last_published_source_id")})
        .build();
  }

  private void deleteOperador(UUID id) throws SQLException {
    executeUpdate("DELETE FROM operador_economico WHERE id = ?", id);
  }

  private void retainName(UUID operadorId, String name) throws SQLException {
    executeUpdate(
        "INSERT INTO operador_economico_nome_alternativo (operador_economico_id, name,"
            + " last_published_date, last_published_source_id) VALUES (?, ?, ?, ?)",
        operadorId, name, Date.valueOf(PUBLISHED_ON), 4711L);
  }

  private UUID insertOperador(String fiscalId, String name) throws SQLException {
    String sql =
        "INSERT INTO operador_economico (id, fiscal_id, name, name_rank_date, name_rank_source_id)"
            + " VALUES (uuidv7(), ?, ?, ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, fiscalId);
      statement.setString(2, name);
      statement.setObject(3, Date.valueOf(PUBLISHED_ON));
      statement.setLong(4, 4711L);
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
