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
      long gcCollectionTimeMillis) {

    static Jvm of(RuntimeMetrics.Jvm jvm) {
      return new Jvm(
          jvm.heapUsedBytes(),
          jvm.heapMaxBytes(),
          jvm.nonHeapUsedBytes(),
          jvm.threadCount(),
          jvm.uptimeMillis(),
          jvm.gcCollectionCount(),
          jvm.gcCollectionTimeMillis());
    }
  }

  @Serdeable
  record SystemLoad(Double cpuLoad) {

    static SystemLoad of(RuntimeMetrics.SystemLoad systemLoad) {
      return new SystemLoad(systemLoad.cpuLoad());
    }
  }

  @Serdeable
  record Http(long requestCount, long errorCount) {

    static Http of(RuntimeMetrics.Http http) {
      return new Http(http.requestCount(), http.errorCount());
    }
  }

  @Serdeable
  record DatastorePool(int active, int idle, int max) {

    static DatastorePool of(RuntimeMetrics.DatastorePool datastorePool) {
      return new DatastorePool(datastorePool.active(), datastorePool.idle(), datastorePool.max());
    }
  }

  static RuntimeMetricsResponse of(RuntimeMetrics sample) {
    return new RuntimeMetricsResponse(
        sample.timestamp(),
        Jvm.of(sample.jvm()),
        SystemLoad.of(sample.system()),
        Http.of(sample.http()),
        DatastorePool.of(sample.datastorePool()));
  }
}
