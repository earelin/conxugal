package gal.conxugal.application.rest.admin.metrics;

import gal.conxugal.domain.metrics.RuntimeMetrics;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/** JSON shape of one {@link RuntimeMetrics} sample — the `data` payload of one SSE event. */
@Serdeable
record RuntimeMetricsResponse(
    Instant timestamp, Jvm jvm, SystemLoad system, Http http, DatastorePool datastorePool) {

  @Serdeable
  record Jvm(
      long heapUsedBytes,
      long heapMaxBytes,
      long nonHeapUsedBytes,
      int threadCount,
      long uptimeMillis,
      long gcCollectionCount,
      long gcCollectionTimeMillis) {}

  @Serdeable
  record SystemLoad(Double cpuLoad) {}

  @Serdeable
  record Http(long requestCount, long errorCount) {}

  @Serdeable
  record DatastorePool(int active, int idle, int max) {}

  static RuntimeMetricsResponse of(RuntimeMetrics sample) {
    return new RuntimeMetricsResponse(
        sample.timestamp(),
        new Jvm(
            sample.jvm().heapUsedBytes(),
            sample.jvm().heapMaxBytes(),
            sample.jvm().nonHeapUsedBytes(),
            sample.jvm().threadCount(),
            sample.jvm().uptimeMillis(),
            sample.jvm().gcCollectionCount(),
            sample.jvm().gcCollectionTimeMillis()),
        new SystemLoad(sample.system().cpuLoad()),
        new Http(sample.http().requestCount(), sample.http().errorCount()),
        new DatastorePool(
            sample.datastorePool().active(),
            sample.datastorePool().idle(),
            sample.datastorePool().max()));
  }
}
