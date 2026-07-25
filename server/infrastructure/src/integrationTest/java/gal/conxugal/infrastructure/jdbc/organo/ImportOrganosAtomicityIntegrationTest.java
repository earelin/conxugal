package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoRepository;
import gal.conxugal.domain.organo.OrganoSourceEntry;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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
 * Proves {@link ImportOrganos}' "single transaction" guarantee (SPEC-0004 R13) against a real
 * Postgres: a write that fails partway through a reconcile run rolls back every write that run
 * made, including ones that had already succeeded earlier in the same run.
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
  OrganoRepository organoRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE organo_contratacion");
    }
  }

  @Test
  void write_failing_partway_through_run_rolls_back_every_write_from_that_run()
      throws Exception {
    insertOrgano("will-be-renamed", "Old Name", true);
    insertOrgano("disappears", "Disappears", true);
    // The injected DataSource shares the same underlying connection as the repository calls
    // below (Micronaut Data's connection-context-aware proxy), so this needs to be committed
    // for the seeded rows to survive the rollback the failing insert forces on that connection.
    try (Connection connection = dataSource.getConnection()) {
      connection.commit();
    }
    String nameTooLongForColumn = "A".repeat(256);
    ImportOrganos importOrganos =
        new ImportOrganos(
            () ->
                List.of(
                    new OrganoSourceEntry("will-be-renamed", "Renamed"),
                    new OrganoSourceEntry("new-and-poisoned", nameTooLongForColumn)),
            organoRepository);

    assertThatThrownBy(importOrganos::run).isInstanceOf(RuntimeException.class);

    // Postgres refuses further commands on the aborted connection until it is rolled back.
    try (Connection rollbackConnection = dataSource.getConnection()) {
      rollbackConnection.rollback();
    }
    List<OrganoDeContratacion> organos = organoRepository.findAll();
    assertThat(organos)
        .extracting(
            OrganoDeContratacion::sourceKey, OrganoDeContratacion::name,
            OrganoDeContratacion::active)
        .containsExactlyInAnyOrder(
            tuple("will-be-renamed", "Old Name", true), tuple("disappears", "Disappears", true));
  }

  private UUID insertOrgano(String sourceKey, String name, boolean active) throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active) "
            + "VALUES (uuidv7(), ?, ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
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
}
