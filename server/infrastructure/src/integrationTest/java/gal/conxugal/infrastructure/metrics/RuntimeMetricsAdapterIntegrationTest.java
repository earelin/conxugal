package gal.conxugal.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.metrics.RuntimeMetrics;
import gal.conxugal.domain.metrics.RuntimeMetricsSource;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuntimeMetricsAdapterIntegrationTest implements TestPropertyProvider {

  private static final String PASSWORD = "s3cr3t-pool-password";
  private static final String USERNAME = "conxugal-metrics-test-user";

  @Container
  static PostgreSQLContainer<?> postgres =
      PostgresContainer.create()
          .withUsername(USERNAME)
          .withPassword(PASSWORD);

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(
        postgres, Map.of("datasources.default.maximum-pool-size", "9"));
  }

  @Inject
  RuntimeMetricsSource runtimeMetricsSource;

  @Inject
  DataSource dataSource;

  @Inject
  DataSourceResolver dataSourceResolver;

  // The injected DataSource is a transaction-aware proxy that only hands out a connection
  // inside an active transaction; borrowing directly from the pool to observe its gauges means
  // going through the resolver to the real, unwrapped Hikari-backed data source underneath.
  private DataSource poolDataSource() {
    return dataSourceResolver.resolve(dataSource);
  }

  @Test
  void reads_live_active_and_idle_connections_from_the_real_pool() throws Exception {
    RuntimeMetrics.DatastorePool before = runtimeMetricsSource.currentSample().datastorePool();

    try (Connection borrowed = poolDataSource().getConnection()) {
      assertThat(borrowed.isClosed()).isFalse();
      RuntimeMetrics.DatastorePool whileBorrowed =
          runtimeMetricsSource.currentSample().datastorePool();

      assertThat(whileBorrowed.active()).isGreaterThan(before.active());
      assertThat(whileBorrowed.max()).isEqualTo(9);
    }

    RuntimeMetrics.DatastorePool afterReturn = runtimeMetricsSource.currentSample().datastorePool();
    assertThat(afterReturn.active()).isEqualTo(before.active());
  }

  @Test
  void assembles_fresh_sample_on_every_call() throws Exception {
    RuntimeMetrics first = runtimeMetricsSource.currentSample();

    try (Connection borrowed = poolDataSource().getConnection()) {
      assertThat(borrowed.isClosed()).isFalse();
      RuntimeMetrics second = runtimeMetricsSource.currentSample();

      assertThat(second).isNotEqualTo(first);
    }
  }

  @Test
  void serialised_sample_carries_no_datastore_credential_or_connection_string() {
    // The application module owns real JSON encoding (TASK-0004's SSE endpoint); the record's
    // generated toString() prints every leaf field the same way an encoder would, which is
    // enough to prove the configured secrets never reach an assembled sample.
    String serialised = runtimeMetricsSource.currentSample().toString();

    assertThat(serialised)
        .doesNotContain(postgres.getPassword())
        .doesNotContain(postgres.getJdbcUrl())
        .doesNotContain(postgres.getUsername());
  }
}
