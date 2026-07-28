package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Verifies V9's schema directly — the collation, the foreign keys' delete rule, and the
 * sibling-name index — rather than through a repository, since none of the JDBC
 * repository code these behaviours will eventually back exists yet.
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
  void foreign_keys_carry_no_on_delete_action() throws Exception {
    assertThat(deleteRulesFor("termo")).containsOnly("NO ACTION");
    assertThat(deleteRulesFor("organo_contratacion")).containsOnly("NO ACTION");
  }

  @Test
  void sibling_index_rejects_two_same_named_children_of_one_parent() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    insertTermo("Fútbol", parentId);

    assertThatThrownBy(() -> insertTermo("Fútbol", parentId))
        .isInstanceOf(SQLException.class);
  }

  @Test
  void sibling_index_rejects_two_same_named_roots() throws Exception {
    insertTermo("Deportes", null);

    assertThatThrownBy(() -> insertTermo("Deportes", null))
        .isInstanceOf(SQLException.class);
  }

  @Test
  void sibling_index_rejects_case_only_difference() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    insertTermo("Fútbol", parentId);

    assertThatThrownBy(() -> insertTermo("FÚTBOL", parentId))
        .isInstanceOf(SQLException.class);
  }

  @Test
  void sibling_index_accepts_the_same_name_under_two_different_parents() throws Exception {
    UUID firstParent = insertTermo("Deportes", null);
    UUID secondParent = insertTermo("Cultura", null);
    insertTermo("Fútbol", firstParent);

    UUID secondId = insertTermo("Fútbol", secondParent);

    assertThat(secondId).isNotNull();
  }

  private List<String> deleteRulesFor(String tableName) throws Exception {
    String sql =
        """
        SELECT rc.delete_rule
        FROM information_schema.table_constraints tc
        JOIN information_schema.referential_constraints rc
          ON tc.constraint_name = rc.constraint_name
        WHERE tc.table_name = ? AND tc.constraint_type = 'FOREIGN KEY'
        """;
    List<String> rules = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          rules.add(resultSet.getString("delete_rule"));
        }
      }
    }
    return rules;
  }

  private UUID insertTermo(String name, UUID parentId) throws Exception {
    // The injected DataSource is Micronaut Data's connection-context-aware proxy, so every
    // call here shares one underlying connection: a failed insert aborts that connection's
    // transaction, and it must be rolled back before any later statement (including a
    // following test's @AfterEach TRUNCATE) can run on it.
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
      try (Connection connection = dataSource.getConnection()) {
        connection.rollback();
      }
      throw e;
    }
  }
}
