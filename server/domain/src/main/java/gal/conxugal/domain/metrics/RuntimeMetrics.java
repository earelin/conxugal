package gal.conxugal.domain.metrics;

import static gal.conxugal.commons.validation.Preconditions.requireNotNegative;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One detailed runtime sample of the running instance, taken at {@code timestamp}.
 *
 * <p>The type carries usage figures only — no connection string, credential, host or key is
 * representable here, so a sample cannot leak one however it is serialised.
 */
public record RuntimeMetrics(
    Instant timestamp,
    Jvm jvm,
    SystemLoad system,
    Http http,
    DatastorePool datastorePool) {

  public RuntimeMetrics {
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    Objects.requireNonNull(jvm, "jvm must not be null");
    Objects.requireNonNull(system, "system must not be null");
    Objects.requireNonNull(http, "http must not be null");
    Objects.requireNonNull(datastorePool, "datastorePool must not be null");
  }

  /**
   * JVM figures read from the platform: memory in bytes, live threads, uptime in milliseconds,
   * and the collection count and accumulated collection time of the garbage collectors.
   */
  public record Jvm(
      long heapUsedBytes,
      long heapMaxBytes,
      long nonHeapUsedBytes,
      int threadCount,
      long uptimeMillis,
      long gcCollectionCount,
      long gcCollectionTimeMillis) {

    public Jvm {
      requireNotNegative(heapUsedBytes, "heapUsedBytes");
      requireNotNegative(heapMaxBytes, "heapMaxBytes");
      requireNotNegative(nonHeapUsedBytes, "nonHeapUsedBytes");
      requireNotNegative(threadCount, "threadCount");
      requireNotNegative(uptimeMillis, "uptimeMillis");
      requireNotNegative(gcCollectionCount, "gcCollectionCount");
      requireNotNegative(gcCollectionTimeMillis, "gcCollectionTimeMillis");
    }
  }

  /**
   * System load. {@code cpuLoad} is the recent system CPU load between {@code 0.0} and
   * {@code 1.0}, or {@code null} on platforms where the JVM does not report it.
   */
  public record SystemLoad(@Nullable Double cpuLoad) {

    public SystemLoad {
      if (cpuLoad != null && (Double.isNaN(cpuLoad) || cpuLoad < 0.0 || cpuLoad > 1.0)) {
        throw new IllegalArgumentException("cpuLoad must be between 0.0 and 1.0, was " + cpuLoad);
      }
    }
  }

  /** Totals served by this instance since it started. */
  public record Http(long requestCount, long errorCount) {

    public Http {
      requireNotNegative(requestCount, "requestCount");
      requireNotNegative(errorCount, "errorCount");
    }
  }

  /** Datastore connection-pool usage — how many connections, never how to reach them. */
  public record DatastorePool(int active, int idle, int max) {

    public DatastorePool {
      requireNotNegative(active, "active");
      requireNotNegative(idle, "idle");
      requireNotNegative(max, "max");
    }
  }
}
