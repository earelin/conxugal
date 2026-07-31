package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.taxonomia.DeleteTermo;
import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoRepository;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
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
 * Proves {@link DeleteTermo}'s single-transaction guarantee against a real Postgres: when the
 * delete fails after the placement clearing has already run, that clearing rolls back and every
 * Órgano stays in the term it was in. A test double cannot show a rollback, so the clearing here
 * is the real JDBC {@code UPDATE}; only the delete is stubbed to fail, which is the one way to
 * reach the window between the two writes deterministically. That stub is why this lives apart
 * from {@link DeleteTermoIntegrationTest} — the replacement is class-wide.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeleteTermoAtomicityIntegrationTest implements TestPropertyProvider {

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

  @Inject
  TermoRepository termoRepository;

  @MockBean(TermoRepository.class)
  TermoRepository termoRepositoryMock() {
    return mock(TermoRepository.class);
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE organo_contratacion, termo");
    }
  }

  @Test
  void delete_failing_after_the_clearing_rolls_that_clearing_back() throws Exception {
    UUID termoId = insertTermo("Deportes");
    insertOrgano("axencia-x", "Axencia X", termoId);
    insertOrgano("axencia-y", "Axencia Y", termoId);
    when(termoRepository.findById(termoId))
        .thenReturn(Optional.of(new Termo(termoId, "Deportes", null)));
    when(termoRepository.existsByParentId(termoId)).thenReturn(false);
    doThrow(new IllegalStateException("delete failed")).when(termoRepository).deleteById(termoId);

    Exception failure = runOnItsOwnThread(() -> deleteTermo.delete(termoId));

    // Asserting the stubbed failure specifically: any RuntimeException would also satisfy the
    // placement assertions below, including one thrown before the clearing ever ran, leaving
    // the test green having proven nothing.
    assertThat(failure)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("delete failed");
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

  // Returns the exception the call threw, so a failure reaching the test thread as data does
  // not silently mask the connection isolation this test relies on (see the class javadoc).
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
