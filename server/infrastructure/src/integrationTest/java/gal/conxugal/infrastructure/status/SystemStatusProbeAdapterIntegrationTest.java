package gal.conxugal.infrastructure.status;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.status.SystemStatus;
import gal.conxugal.domain.status.SystemStatusProbe;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemStatusProbeAdapterIntegrationTest implements TestPropertyProvider {

  private static final String PASSWORD = "s3cr3t-status-password";
  private static final String USERNAME = "conxugal-status-test-user";

  @Container
  static PostgreSQLContainer<?> postgres =
      PostgresContainer.create()
          .withUsername(USERNAME)
          .withPassword(PASSWORD);

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(
        postgres, Map.of("flyway.datasources.default.enabled", "false"));
  }

  @Inject
  SystemStatusProbe systemStatusProbe;

  @Test
  @Order(1)
  void reports_up_and_reachable_when_the_real_database_is_up() {
    SystemStatus status = systemStatusProbe.currentStatus();

    assertThat(status.status()).isEqualTo(SystemStatus.ServiceState.UP);
    assertThat(status.datastore().reachable()).isTrue();
    assertThat(status.application().version()).isNotBlank();
    assertThat(status.application().environment()).isNotBlank();
    assertThat(status.runtime().javaVersion()).isNotBlank();
    assertThat(status.runtime().osName()).isNotBlank();
    assertThat(status.runtime().uptimeMillis()).isPositive();
    assertThat(status.runtime().memoryUsedBytes()).isPositive();
    assertThat(status.runtime().memoryMaxBytes()).isPositive();
  }

  @Test
  @Order(2)
  void serialised_status_carries_no_datastore_credential_or_connection_string() {
    // The application module owns real JSON encoding; the record's generated toString() prints
    // every leaf field the same way an encoder would, which is enough to prove the configured
    // secrets never reach an assembled status.
    String serialised = systemStatusProbe.currentStatus().toString();

    assertThat(serialised)
        .doesNotContain(postgres.getPassword())
        .doesNotContain(postgres.getJdbcUrl())
        .doesNotContain(postgres.getUsername());
  }

  @Test
  @Order(3)
  void reflects_datastore_becoming_unreachable_on_the_next_call() {
    assertThat(systemStatusProbe.currentStatus().datastore().reachable()).isTrue();

    postgres.stop();

    SystemStatus status = systemStatusProbe.currentStatus();
    assertThat(status.status()).isEqualTo(SystemStatus.ServiceState.DEGRADED);
    assertThat(status.datastore().reachable()).isFalse();
  }
}
