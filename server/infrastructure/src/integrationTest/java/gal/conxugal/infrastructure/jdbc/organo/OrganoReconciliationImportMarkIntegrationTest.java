package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.ImportOutcome;
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
import java.sql.Statement;
import java.util.List;
import java.util.Map;
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
 * Proves the import mark is invisible to catalogue reconciliation against a real Postgres: a
 * full run that refreshes, deactivates, reactivates and inserts leaves every Órgano's mark
 * exactly as it found it. Only the database can answer this — a mocked repository shows which
 * port methods the reconciler calls, not which columns the statements behind them write.
 *
 * <p>The DI-managed {@code ImportOrganos} bean is injected rather than constructed, so its call
 * into the reconciler resolves to the bean carrying the {@code @Transactional} advice and the
 * run really does commit. Unlike {@code ImportOrganosAtomicityIntegrationTest} it needs no
 * separate thread: the fixtures are committed through their own raw connections before the run
 * starts, and the assertions read after it has returned.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganoReconciliationImportMarkIntegrationTest implements TestPropertyProvider {

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
  DataSource dataSource;

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
  void full_reconciliation_leaves_every_import_mark_as_it_found_it() throws Exception {
    insertOrgano("refreshed", "Old Name", true, true);
    insertOrgano("reactivated", "Reactivated", false, true);
    insertOrgano("deactivated", "Deactivated", true, true);
    insertOrgano("unmarked", "Unmarked", true, false);
    when(organoSource.fetchAll())
        .thenReturn(
            List.of(
                new OrganoSourceEntry("refreshed", "New Name"),
                new OrganoSourceEntry("reactivated", "Reactivated"),
                new OrganoSourceEntry("unmarked", "Unmarked"),
                new OrganoSourceEntry("brand-new", "Brand New")));

    ImportOutcome outcome = importOrganos.run();

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.SUCCESS);
    Table organos = AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("organo_contratacion")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("name")})
        .build();
    assertThat(organos).hasNumberOfRows(5);
    assertOrgano(organos, 0, "Brand New", true, false);
    assertOrgano(organos, 1, "Deactivated", false, true);
    assertOrgano(organos, 2, "New Name", true, true);
    assertOrgano(organos, 3, "Reactivated", true, true);
    assertOrgano(organos, 4, "Unmarked", true, false);
  }

  private static void assertOrgano(
      Table organos, int index, String name, boolean active, boolean importable) {
    assertThat(organos).row(index).value("name").isEqualTo(name);
    assertThat(organos).row(index).value("active").isEqualTo(active);
    assertThat(organos).row(index).value("importable").isEqualTo(importable);
  }

  private static void insertOrgano(String sourceKey, String name, boolean active,
      boolean importable) throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, importable) "
            + "VALUES (uuidv7(), ?, ?, ?, ?)";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, name);
      statement.setBoolean(3, active);
      statement.setBoolean(4, importable);
      statement.executeUpdate();
    }
  }

  private static Connection rawConnection() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
