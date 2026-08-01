package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

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
import org.assertj.db.type.AssertDbConnection;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies V9's schema directly — the collation, what the foreign keys refuse on delete, the
 * self-parent check, and the sibling-name index — rather than through a repository, since
 * none of the JDBC repository code these behaviours will eventually back exists yet.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TermoMigrationIntegrationTest implements TestPropertyProvider {

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
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE organo_contratacion, termo");
    }
  }

  @Test
  void collation_orders_accented_galician_names_correctly() throws Exception {
    insertTermo("Zamora", null);
    insertTermo("Ávila", null);

    List<String> names = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery("SELECT name FROM termo ORDER BY name COLLATE \"galician\"")) {
      while (resultSet.next()) {
        names.add(resultSet.getString("name"));
      }
    }

    assertThat(names).containsExactly("Ávila", "Zamora");
  }

  @Test
  void bare_order_by_name_inherits_the_galician_collation_from_the_column() throws Exception {
    insertTermo("Zamora", null);
    insertTermo("Ávila", null);

    List<String> names = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT name FROM termo ORDER BY name")) {
      while (resultSet.next()) {
        names.add(resultSet.getString("name"));
      }
    }

    assertThat(names).containsExactly("Ávila", "Zamora");
  }

  @Test
  void delete_is_rejected_while_the_term_has_children() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    insertTermo("Fútbol", parentId);

    assertThatThrownBy(() -> deleteTermo(parentId))
        .isInstanceOfSatisfying(SQLException.class, this::assertViolatesParentForeignKey);
  }

  @Test
  void delete_unclassifies_the_organos_placed_in_the_term_without_deleting_them() throws Exception {
    UUID termoId = insertTermo("Deportes", null);
    insertOrgano("consorcio-x", "Consorcio X", termoId);

    deleteTermo(termoId);

    // ON DELETE SET NULL, never CASCADE: R16 returns the Órgano to unclassified and keeps it.
    AssertDbConnection assertDbConnection = AssertDbConnectionFactory.of(dataSource).create();
    Table organos = assertDbConnection.table("organo_contratacion").build();
    assertThat(organos).hasNumberOfRows(1);
    assertThat(organos)
        .row(0)
            .value("source_key").isEqualTo("consorcio-x")
            .value("termo_id").isNull();
  }

  @Test
  void delete_is_accepted_once_nothing_references_the_term() throws Exception {
    UUID termoId = insertTermo("Deportes", null);

    deleteTermo(termoId);

    AssertDbConnection assertDbConnection = AssertDbConnectionFactory.of(dataSource).create();
    assertThat(assertDbConnection.table("termo").build()).hasNumberOfRows(0);
  }

  @Test
  void check_rejects_inserting_term_as_its_own_parent() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> insertTermoWithId(id, "Deportes", id))
        .isInstanceOfSatisfying(SQLException.class, this::assertViolatesSelfParentCheck);
  }

  @Test
  void check_rejects_re_parenting_term_onto_itself() throws Exception {
    UUID id = insertTermo("Deportes", null);

    assertThatThrownBy(() -> executeUpdate("UPDATE termo SET parent_id = id WHERE id = ?", id))
        .isInstanceOfSatisfying(SQLException.class, this::assertViolatesSelfParentCheck);
  }

  @Test
  void sibling_index_rejects_two_same_named_children_of_one_parent() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    insertTermo("Fútbol", parentId);

    assertThatThrownBy(() -> insertTermo("Fútbol", parentId))
        .isInstanceOfSatisfying(SQLException.class, this::assertViolatesSiblingNameIndex);
  }

  @Test
  void sibling_index_rejects_two_same_named_roots() throws Exception {
    insertTermo("Deportes", null);

    assertThatThrownBy(() -> insertTermo("Deportes", null))
        .isInstanceOfSatisfying(SQLException.class, this::assertViolatesSiblingNameIndex);
  }

  @Test
  void sibling_index_rejects_case_only_difference() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    insertTermo("Fútbol", parentId);

    assertThatThrownBy(() -> insertTermo("FÚTBOL", parentId))
        .isInstanceOfSatisfying(SQLException.class, this::assertViolatesSiblingNameIndex);
  }

  @Test
  void sibling_index_accepts_the_same_name_under_two_different_parents() throws Exception {
    UUID firstParent = insertTermo("Deportes", null);
    UUID secondParent = insertTermo("Cultura", null);
    insertTermo("Fútbol", firstParent);

    UUID secondId = insertTermo("Fútbol", secondParent);

    assertThat(secondId).isNotNull();
  }

  private void assertViolatesSiblingNameIndex(SQLException exception) {
    // SQLSTATE 23505 is unique_violation; pinning the constraint name too rules out a
    // coincidental different constraint (e.g. the primary key) failing for the wrong reason.
    assertThat(exception.getSQLState()).isEqualTo("23505");
    assertThat(exception.getMessage()).contains("termo_sibling_name_key");
  }

  private void assertViolatesSelfParentCheck(SQLException exception) {
    // SQLSTATE 23514 is check_violation.
    assertThat(exception.getSQLState()).isEqualTo("23514");
    assertThat(exception.getMessage()).contains("termo_parent_not_self");
  }

  private void assertViolatesParentForeignKey(SQLException exception) {
    assertViolatesForeignKey(exception, "termo_parent_id_fkey");
  }

  private void assertViolatesForeignKey(SQLException exception, String constraintName) {
    // SQLSTATE 23503 is foreign_key_violation — the delete was refused rather than cascading,
    // which is the whole point of leaving the parent key without an ON DELETE action.
    assertThat(exception.getSQLState()).isEqualTo("23503");
    assertThat(exception.getMessage()).contains(constraintName);
  }

  private void deleteTermo(UUID id) throws SQLException {
    executeUpdate("DELETE FROM termo WHERE id = ?", id);
  }

  private void insertTermoWithId(UUID id, String name, UUID parentId) throws SQLException {
    executeUpdate(
        "INSERT INTO termo (id, name, parent_id) VALUES (?, ?, ?)", id, name, parentId);
  }

  private void insertOrgano(String sourceKey, String name, UUID termoId) throws SQLException {
    executeUpdate(
        "INSERT INTO organo_contratacion (id, source_key, name, active, termo_id) "
            + "VALUES (uuidv7(), ?, ?, TRUE, ?)",
        sourceKey, name, termoId);
  }

  private UUID insertTermo(String name, UUID parentId) throws SQLException {
    String sql =
        "INSERT INTO termo (id, name, parent_id) VALUES (uuidv7(), ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      statement.setObject(2, parentId);
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
