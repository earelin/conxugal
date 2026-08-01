package gal.conxugal.infrastructure.status;

import gal.conxugal.domain.status.SystemStatus;
import gal.conxugal.domain.status.SystemStatusProbe;
import gal.conxugal.domain.time.Clock;
import gal.conxugal.infrastructure.version.ApplicationVersion;
import io.micronaut.context.env.Environment;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.sql.DataSource;

/**
 * {@link SystemStatusProbe} adapter assembling one fresh snapshot per call: datastore
 * reachability via a borrowed connection's own JDBC validity check, plus coarse JVM/OS figures
 * from the platform.
 *
 * <p>Reads only whether the datastore answered — never its JDBC URL, user or password; a failed
 * connection attempt is folded to "unreachable" without inspecting the underlying exception,
 * which could otherwise carry a host or credential in its message.
 */
@Singleton
public class SystemStatusProbeAdapter implements SystemStatusProbe {

  private static final int VALIDATION_TIMEOUT_SECONDS = 2;
  private static final String UNKNOWN = "unknown";

  private final Clock clock;
  private final DataSource dataSource;
  private final SystemStatus.ApplicationInfo applicationInfo;
  private final String javaVersion;
  private final String javaVendor;
  private final String osName;
  private final String osArch;

  @Inject
  public SystemStatusProbeAdapter(
      Clock clock,
      @Named("default") DataSource dataSource,
      DataSourceResolver dataSourceResolver,
      Environment environment) {
    this(
        clock,
        dataSourceResolver.resolve(dataSource),
        environmentLabel(environment),
        ApplicationVersion.read());
  }

  SystemStatusProbeAdapter(
      Clock clock, DataSource dataSource, String environmentLabel, String applicationVersion) {
    this.clock = clock;
    this.dataSource = dataSource;
    this.applicationInfo = new SystemStatus.ApplicationInfo(applicationVersion, environmentLabel);
    this.javaVersion = System.getProperty("java.version", UNKNOWN);
    this.javaVendor = System.getProperty("java.vendor", UNKNOWN);
    this.osName = System.getProperty("os.name", UNKNOWN);
    this.osArch = System.getProperty("os.arch", UNKNOWN);
  }

  @Override
  public SystemStatus currentStatus() {
    return SystemStatus.assess(
        datastoreReachable(), clock.instant(), applicationInfo, readRuntimeInfo());
  }

  private boolean datastoreReachable() {
    try (Connection connection = dataSource.getConnection()) {
      return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
    } catch (SQLException e) {
      return false;
    }
  }

  private SystemStatus.RuntimeInfo readRuntimeInfo() {
    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    return new SystemStatus.RuntimeInfo(
        javaVersion,
        javaVendor,
        ManagementFactory.getRuntimeMXBean().getUptime(),
        heap.getUsed(),
        Math.max(0L, heap.getMax()),
        osName,
        osArch);
  }

  private static String environmentLabel(Environment environment) {
    SortedSet<String> activeNames = new TreeSet<>(environment.getActiveNames());
    return activeNames.isEmpty() ? "production" : String.join(",", activeNames);
  }
}
