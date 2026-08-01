package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Proves {@link DeleteTermo} against a real Postgres on both counts the R16 rules make: the
 * Órganos placed in the deleted term are returned to unclassified and every one of them
 * survives, and a failure between the clearing and the delete rolls the clearing back.
 *
 * <p>The failing case is provoked with a {@code BEFORE DELETE} trigger rather than a stubbed
 * repository, so both statements are the adapter's own and the rollback is the database's. A
 * test double in place of {@code TermoRepository} would mean the delete never ran, leaving
 * "the term is still there" true for the wrong reason. The trigger is the lever available
 * because the container's role is a superuser, so revoking {@code DELETE} would not bite.
 *
 * <p>Both cases inject the DI-managed bean so the {@code @Transactional} advice applies, and
 * run it on their own thread so its transaction borrows a connection independent of the one
 * this test's JDBC work uses — Micronaut Data JDBC otherwise binds one shared connection to
 * the calling thread, which would let the assertions read that connection's ambient,
 * not-yet-committed state instead of what the use case actually committed.
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
      statement.execute("DROP TRIGGER IF EXISTS termo_delete_fails ON termo");
      statement.execute("DROP FUNCTION IF EXISTS fail_termo_delete()");
      statement.execute("TRUNCATE TABLE organo_contratacion, termo");
    }
  }

  @Test
  void deleting_term_unclassifies_its_organos_and_deletes_none_of_them() throws Exception {
    UUID deletedTermoId = insertTermo("Deportes");
    UUID survivingTermoId = insertTermo("Cultura");
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
            .value("termo_id").isEqualTo(survivingTermoId);
    Table termos = termoTable();
    assertThat(termos).hasNumberOfRows(1);
    assertThat(termos)
        .row(0)
            .value("id").isEqualTo(survivingTermoId);
  }

  @Test
  void delete_failing_after_the_clearing_rolls_that_clearing_back() throws Exception {
    UUID termoId = insertTermo("Deportes");
    insertOrgano("axencia-x", "Axencia X", termoId);
    insertOrgano("axencia-y", "Axencia Y", termoId);
    failEveryTermoDelete();

    Exception failure = runOnItsOwnThread(() -> deleteTermo.delete(termoId));

    // Matching the trigger's own message: any RuntimeException would also satisfy the
    // assertions below, including one raised before the clearing ever ran, which would
    // leave this test green having proven nothing.
    assertThat(failure).hasStackTraceContaining("termo delete refused");
    Table organos = organoTable();
    assertThat(organos).hasNumberOfRows(2);
    assertThat(organos)
        .row(0)
            .value("name").isEqualTo("Axencia X")
            .value("termo_id").isEqualTo(termoId)
        .row(1)
            .value("name").isEqualTo("Axencia Y")
            .value("termo_id").isEqualTo(termoId);
    Table termos = termoTable();
    assertThat(termos).hasNumberOfRows(1);
    assertThat(termos)
        .row(0)
            .value("id").isEqualTo(termoId);
  }

  // Makes the adapter's own DELETE fail once it reaches the database, which is the only point
  // where the use case has already issued the placement-clearing UPDATE.
  private static void failEveryTermoDelete() throws Exception {
    try (Connection connection = rawConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE FUNCTION fail_termo_delete() RETURNS TRIGGER AS $$
            BEGIN RAISE EXCEPTION 'termo delete refused'; END;
          $$ LANGUAGE plpgsql
          """);
      statement.execute(
          """
          CREATE TRIGGER termo_delete_fails BEFORE DELETE ON termo
            FOR EACH ROW EXECUTE FUNCTION fail_termo_delete()
          """);
    }
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
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
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
  private static UUID insertTermo(String name) throws Exception {
    String sql = "INSERT INTO termo (id, name) VALUES (uuidv7(), ?) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
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
