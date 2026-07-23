package gal.conxugal.infrastructure.metrics;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import gal.conxugal.domain.metrics.HttpRequestCounter;
import gal.conxugal.domain.metrics.RuntimeMetrics;
import gal.conxugal.domain.metrics.RuntimeMetricsSource;
import gal.conxugal.domain.time.Clock;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import javax.sql.DataSource;

/**
 * {@link RuntimeMetricsSource} adapter assembling one fresh sample per call from the running
 * instance: JVM figures via the platform MXBeans, CPU load via the operating-system MXBean where
 * the platform reports one, HTTP totals from {@link HttpRequestCounter}, and connection-pool
 * usage from the Hikari pool's own gauges.
 *
 * <p>Reads only usage figures from the pool — active/idle connections and the configured maximum
 * pool size — never its JDBC URL, user or password.
 */
@Singleton
public class RuntimeMetricsAdapter implements RuntimeMetricsSource {

  private final Clock clock;
  private final HttpRequestCounter httpRequestCounter;
  private final HikariPoolMXBean pool;
  private final int poolMaxSize;

  @Inject
  public RuntimeMetricsAdapter(
      Clock clock,
      HttpRequestCounter httpRequestCounter,
      @Named("default") DataSource dataSource,
      DataSourceResolver dataSourceResolver) {
    this(clock, httpRequestCounter, asHikari(dataSourceResolver.resolve(dataSource)));
  }

  private RuntimeMetricsAdapter(
      Clock clock, HttpRequestCounter httpRequestCounter, HikariDataSource dataSource) {
    this(
        clock,
        httpRequestCounter,
        dataSource.getHikariPoolMXBean(),
        dataSource.getMaximumPoolSize());
  }

  RuntimeMetricsAdapter(
      Clock clock, HttpRequestCounter httpRequestCounter, HikariPoolMXBean pool, int poolMaxSize) {
    this.clock = clock;
    this.httpRequestCounter = httpRequestCounter;
    this.pool = pool;
    this.poolMaxSize = poolMaxSize;
  }

  @Override
  public RuntimeMetrics currentSample() {
    return new RuntimeMetrics(
        clock.instant(), readJvm(), readSystemLoad(), httpRequestCounter.snapshot(), readPool());
  }

  private static RuntimeMetrics.Jvm readJvm() {
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

    long gcCollectionCount = 0;
    long gcCollectionTimeMillis = 0;
    for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
      gcCollectionCount += nonNegative(collector.getCollectionCount());
      gcCollectionTimeMillis += nonNegative(collector.getCollectionTime());
    }

    return new RuntimeMetrics.Jvm(
        memory.getHeapMemoryUsage().getUsed(),
        nonNegative(memory.getHeapMemoryUsage().getMax()),
        memory.getNonHeapMemoryUsage().getUsed(),
        threads.getThreadCount(),
        runtime.getUptime(),
        gcCollectionCount,
        gcCollectionTimeMillis);
  }

  /**
   * The JDK documents {@code -1} as the sentinel for "undefined on this platform" for both
   * {@link java.lang.management.MemoryUsage#getMax()} and a garbage collector's collection
   * count/time; folding it to {@code 0} here, rather than passing it through, keeps such a
   * platform from failing the whole sample.
   */
  private static long nonNegative(long value) {
    return Math.max(0L, value);
  }

  /** Package-visible so a unit test can exercise both the HotSpot and the plain-platform path. */
  static RuntimeMetrics.SystemLoad systemLoadFrom(OperatingSystemMXBean operatingSystem) {
    if (operatingSystem
        instanceof com.sun.management.OperatingSystemMXBean hotSpotOperatingSystem) {
      double cpuLoad = hotSpotOperatingSystem.getCpuLoad();
      if (cpuLoad >= 0.0) {
        return new RuntimeMetrics.SystemLoad(cpuLoad);
      }
    }
    return new RuntimeMetrics.SystemLoad(null);
  }

  private static RuntimeMetrics.SystemLoad readSystemLoad() {
    return systemLoadFrom(ManagementFactory.getOperatingSystemMXBean());
  }

  private RuntimeMetrics.DatastorePool readPool() {
    return new RuntimeMetrics.DatastorePool(
        pool.getActiveConnections(), pool.getIdleConnections(), poolMaxSize);
  }

  private static HikariDataSource asHikari(DataSource dataSource) {
    if (dataSource instanceof HikariDataSource hikariDataSource) {
      return hikariDataSource;
    }
    throw new IllegalStateException(
        "RuntimeMetricsAdapter requires a Hikari-backed DataSource, got "
            + dataSource.getClass());
  }
}
