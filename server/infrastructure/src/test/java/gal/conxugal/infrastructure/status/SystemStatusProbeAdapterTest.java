package gal.conxugal.infrastructure.status;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.status.SystemStatus;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SystemStatusProbeAdapterTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-07-18T09:30:00Z");
  private static final String ENVIRONMENT_LABEL = "local";
  private static final String APPLICATION_VERSION = "0.1.0-test";

  @Test
  void reports_up_and_reachable_when_the_connection_is_valid() {
    SystemStatusProbeAdapter adapter = adapterWith(fakeDataSource(fakeConnection(true)));

    SystemStatus status = adapter.currentStatus();

    assertThat(status.status()).isEqualTo(SystemStatus.ServiceState.UP);
    assertThat(status.datastore().reachable()).isTrue();
    assertThat(status.checkedAt()).isEqualTo(FIXED_INSTANT);
  }

  @Test
  void reports_degraded_and_unreachable_when_the_connection_is_invalid() {
    SystemStatusProbeAdapter adapter = adapterWith(fakeDataSource(fakeConnection(false)));

    SystemStatus status = adapter.currentStatus();

    assertThat(status.status()).isEqualTo(SystemStatus.ServiceState.DEGRADED);
    assertThat(status.datastore().reachable()).isFalse();
  }

  @Test
  void reports_degraded_and_unreachable_when_borrowing_connection_fails() {
    SystemStatusProbeAdapter adapter =
        adapterWith(throwingDataSource(new SQLException("connection refused")));

    SystemStatus status = adapter.currentStatus();

    assertThat(status.status()).isEqualTo(SystemStatus.ServiceState.DEGRADED);
    assertThat(status.datastore().reachable()).isFalse();
  }

  @Test
  void reports_the_injected_application_version_and_environment() {
    SystemStatusProbeAdapter adapter = adapterWith(fakeDataSource(fakeConnection(true)));

    SystemStatus.ApplicationInfo application = adapter.currentStatus().application();

    assertThat(application.version()).isEqualTo(APPLICATION_VERSION);
    assertThat(application.environment()).isEqualTo(ENVIRONMENT_LABEL);
  }

  @Test
  void reports_plausible_runtime_figures_from_the_running_platform() {
    SystemStatusProbeAdapter adapter = adapterWith(fakeDataSource(fakeConnection(true)));

    SystemStatus.RuntimeInfo runtime = adapter.currentStatus().runtime();

    assertThat(runtime.javaVersion()).isEqualTo(System.getProperty("java.version"));
    assertThat(runtime.javaVendor()).isEqualTo(System.getProperty("java.vendor"));
    assertThat(runtime.osName()).isEqualTo(System.getProperty("os.name"));
    assertThat(runtime.osArch()).isEqualTo(System.getProperty("os.arch"));
    assertThat(runtime.uptimeMillis()).isPositive();
    assertThat(runtime.memoryUsedBytes()).isPositive();
    assertThat(runtime.memoryMaxBytes()).isPositive();
  }

  private static SystemStatusProbeAdapter adapterWith(DataSource dataSource) {
    return new SystemStatusProbeAdapter(
        () -> FIXED_INSTANT, dataSource, ENVIRONMENT_LABEL, APPLICATION_VERSION);
  }

  private static Connection fakeConnection(boolean valid) {
    return (Connection)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if ("isValid".equals(method.getName())) {
                return valid;
              }
              if ("close".equals(method.getName())) {
                return null;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static DataSource fakeDataSource(Connection connection) {
    return (DataSource)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
              if ("getConnection".equals(method.getName()) && args == null) {
                return connection;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static DataSource throwingDataSource(SQLException exception) {
    return (DataSource)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
              if ("getConnection".equals(method.getName()) && args == null) {
                throw exception;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }
}
