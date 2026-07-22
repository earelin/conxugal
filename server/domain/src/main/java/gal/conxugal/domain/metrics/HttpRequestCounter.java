package gal.conxugal.domain.metrics;

import jakarta.inject.Singleton;
import java.util.concurrent.atomic.LongAdder;

/**
 * Process-lifetime totals of HTTP requests served and responses that failed (a {@code 5xx}
 * status or a thrown error), fed by a filter in the {@code application} module and
 * read into a {@link RuntimeMetrics.Http} snapshot by an adapter in {@code infrastructure}.
 *
 * <p>Held only in memory: nothing here is persisted, and there is no reset or query for a past
 * value.
 */
@Singleton
public final class HttpRequestCounter {

  private final LongAdder requests = new LongAdder();
  private final LongAdder errors = new LongAdder();

  public void recordRequest() {
    requests.increment();
  }

  public void recordError() {
    errors.increment();
  }

  public RuntimeMetrics.Http snapshot() {
    return new RuntimeMetrics.Http(requests.sum(), errors.sum());
  }
}
