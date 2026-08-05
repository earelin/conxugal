package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoRepository;
import gal.conxugal.domain.organo.OrganoSource;
import gal.conxugal.domain.organo.OrganoSourceEntry;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link ImportOrganos}' single-transaction guarantee against a real Postgres: a write
 * that fails partway through a reconcile run rolls back every write that run made, including
 * ones that had already succeeded earlier in the same run. Injects the DI-managed
 * {@code ImportOrganos} bean rather than constructing it, so its call into the reconciler
 * resolves to the DI-managed {@code OrganoReconciler} bean carrying the {@code @Transactional}
 * advice, not a plain instance, and runs it on its own thread so its transaction borrows a
 * connection independent of the one this test's own JDBC work uses — Micronaut Data JDBC
 * otherwise binds one shared connection to the whole calling thread, which would let the final
 * assertion see that connection's ambient, not-yet-committed state instead of what the
 * reconciler's own transaction actually committed.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportOrganosAtomicityIntegrationTest implements TestPropertyProvider {

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
  ImportOrganos importOrganos;

  @Inject
  OrganoSource organoSource;

  @Inject
  OrganoRepository organoRepository;

  @MockBean(OrganoSource.class)
  OrganoSource organoSourceMock() {
    return mock(OrganoSource.class);
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE contrato_menor, organo_contratacion");
    }
  }

  @Test
  void write_failing_partway_through_run_rolls_back_every_write_from_that_run()
      throws Exception {
    insertOrgano("will-be-renamed", "Old Name", true);
    insertOrgano("disappears", "Disappears", true);
    String nameTooLongForColumn = "A".repeat(256);
    when(organoSource.fetchAll())
        .thenReturn(
            List.of(
                new OrganoSourceEntry("will-be-renamed", "Renamed"),
                new OrganoSourceEntry("new-and-poisoned", nameTooLongForColumn)));

    Exception failure = runOnItsOwnThread(importOrganos::run);

    assertThat(failure).isInstanceOf(RuntimeException.class);
    List<OrganoDeContratacion> organos = organoRepository.findAllOrderByName();
    assertThat(organos)
        .extracting(
            OrganoDeContratacion::sourceKey, OrganoDeContratacion::name,
            OrganoDeContratacion::active)
        .containsExactlyInAnyOrder(
            tuple("will-be-renamed", "Old Name", true), tuple("disappears", "Disappears", true));
  }

  // Runs the given call on its own thread and returns the exception it threw, so a failure
  // that reaches the test thread as data does not silently mask the connection isolation this
  // test relies on (see the class javadoc).
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

  private UUID insertOrgano(String sourceKey, String name, boolean active) throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active) "
            + "VALUES (uuidv7(), ?, ?, ?) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, name);
      statement.setBoolean(3, active);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return resultSet.getObject("id", UUID.class);
      }
    }
  }

  private static Connection rawConnection() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
