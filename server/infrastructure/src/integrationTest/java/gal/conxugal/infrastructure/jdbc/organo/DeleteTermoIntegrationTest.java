package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.organo.taxonomia.DeleteTermo;
import gal.conxugal.domain.organo.taxonomia.TermoId;
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
import java.util.concurrent.Callable;
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
 * Proves the R16 reassignment end to end against a real Postgres: deleting a term through
 * {@link DeleteTermo} returns the Órganos placed in it to the unclassified set, leaves every
 * one of them in the catalogue, and touches no other term's placements. The clearing itself is
 * the {@code ON DELETE SET NULL} foreign key's, so this is where that wiring is proven — the
 * use case issues one delete and never names a placement.
 *
 * <p>Injects the DI-managed bean rather than constructing it, and runs it on its own thread so
 * its write borrows a connection independent of the one this test's own JDBC work uses:
 * {@code @MicronautTest} wraps each test in a transaction that is rolled back, which a
 * same-thread call would join, hiding the committed result the assertions below read back
 * through a raw connection.
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
      statement.execute("TRUNCATE TABLE contrato_menor, organo_contratacion, termo");
    }
  }

  @Test
  void deleting_term_unclassifies_its_organos_and_deletes_none_of_them() throws Exception {
    TermoId deletedTermoId = insertTermo("Deportes");
    TermoId survivingTermoId = insertTermo("Cultura");
    insertOrgano("axencia-x", "Axencia X", deletedTermoId);
    insertOrgano("axencia-y", "Axencia Y", deletedTermoId);
    insertOrgano("axencia-z", "Axencia Z", survivingTermoId);

    Exception failure = runOnItsOwnThread(() -> deleteTermo.delete(deletedTermoId));

    assertThat(failure).isNull();
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
            .value("termo_id").isEqualTo(survivingTermoId.value());
    Table termos = termoTable();
    assertThat(termos).hasNumberOfRows(1);
    assertThat(termos)
        .row(0)
            .value("id").isEqualTo(survivingTermoId.value());
  }

  private static Table organoTable() {
    return table("organo_contratacion");
  }

  private static Table termoTable() {
    return table("termo");
  }

  // Ordered by name so row(n) is stable, rather than leaning on uuidv7 insertion order.
  private static Table table(String name) {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table(name)
        .columnsToOrder(new Table.Order[] {Table.Order.asc("name")})
        .build();
  }

  // Returns the exception the call threw, or null, so a failure reaching the test thread as
  // data does not silently mask the connection isolation this test relies on.
  private static Exception runOnItsOwnThread(Runnable call) throws Exception {
    Callable<Exception> captureFailure =
        () -> {
          try {
            call.run();
            return null;
          } catch (RuntimeException e) {
            return e;
          }
        };
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      return executor.submit(captureFailure).get(10, TimeUnit.SECONDS);
    }
  }

  // Raw, auto-committing connections rather than the injected DataSource: the use case runs on
  // another thread, so it only sees this setup once it is committed.
  private static TermoId insertTermo(String name) throws Exception {
    String sql = "INSERT INTO termo (id, name) VALUES (uuidv7(), ?) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      return new TermoId(generatedId(statement));
    }
  }

  private static void insertOrgano(String sourceKey, String name, TermoId termoId)
      throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, termo_id) "
            + "VALUES (uuidv7(), ?, ?, TRUE, ?) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, name);
      statement.setObject(3, termoId.value());
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
