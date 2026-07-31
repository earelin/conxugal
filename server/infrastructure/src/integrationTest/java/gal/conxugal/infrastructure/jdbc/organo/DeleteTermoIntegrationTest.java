package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.organo.taxonomia.DeleteTermo;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link DeleteTermo}'s R16 reassignment against a real Postgres: the Órganos placed in
 * the deleted term are returned to unclassified and every one of them survives. Injects the
 * DI-managed bean so the {@code @Transactional} advice applies, and runs it on its own thread
 * so its transaction borrows a connection independent of the one this test's own JDBC work
 * uses — Micronaut Data JDBC otherwise binds one shared connection to the calling thread.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeleteTermoIntegrationTest implements TestPropertyProvider {

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
  DeleteTermo deleteTermo;

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE organo_contratacion, termo");
    }
  }

  @Test
  void deleting_term_unclassifies_its_organos_and_deletes_none_of_them() throws Exception {
    UUID deletedTermoId = insertTermo("Deportes", null);
    UUID survivingTermoId = insertTermo("Cultura", null);
    insertOrgano("axencia-x", "Axencia X", deletedTermoId);
    insertOrgano("axencia-y", "Axencia Y", deletedTermoId);
    insertOrgano("axencia-z", "Axencia Z", survivingTermoId);

    runOnItsOwnThread(() -> deleteTermo.delete(deletedTermoId));

    Table organos = organoTable();
    assertThat(organos).hasNumberOfRows(3);
    assertThat(organos)
        .row(0)
            .value("name").isEqualTo("Axencia X")
            .value("termo_id").isNull()
        .row(1)
            .value("name").isEqualTo("Axencia Y")
            .value("termo_id").isNull()
        .row(2)
            .value("name").isEqualTo("Axencia Z")
            .value("termo_id").isEqualTo(survivingTermoId);
    Table termos = termoTable();
    assertThat(termos).hasNumberOfRows(1);
    assertThat(termos)
        .row(0)
            .value("id").isEqualTo(survivingTermoId);
  }

  private static Table organoTable() {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table("organo_contratacion")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("name")})
        .build();
  }

  private static Table termoTable() {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table("termo")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("name")})
        .build();
  }

  private static void runOnItsOwnThread(Runnable call) throws Exception {
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      executor.submit(call).get(10, TimeUnit.SECONDS);
    }
  }

  // Raw, auto-committing connections rather than the injected DataSource: the use case runs on
  // another thread, so it only sees this setup once it is committed.
  private static UUID insertTermo(String name, UUID parentId) throws Exception {
    String sql = "INSERT INTO termo (id, name, parent_id) VALUES (uuidv7(), ?, ?) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      statement.setObject(2, parentId);
      return generatedId(statement);
    }
  }

  private static void insertOrgano(String sourceKey, String name, UUID termoId) throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, termo_id) "
            + "VALUES (uuidv7(), ?, ?, TRUE, ?) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, name);
      statement.setObject(3, termoId);
      generatedId(statement);
    }
  }

  private static UUID generatedId(PreparedStatement statement) throws Exception {
    try (ResultSet resultSet = statement.executeQuery()) {
      if (!resultSet.next()) {
        throw new IllegalStateException("Insert did not return a generated id");
      }
      return resultSet.getObject("id", UUID.class);
    }
  }

  private static Connection rawConnection() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
