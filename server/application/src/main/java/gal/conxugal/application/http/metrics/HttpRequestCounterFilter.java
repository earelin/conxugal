package gal.conxugal.application.http.metrics;

import gal.conxugal.domain.metrics.HttpRequestCounter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;

/**
 * Feeds {@link HttpRequestCounter} for every request, including requests to the metrics stream
 * itself. This response-filter signature is invoked for both a normal response and a thrown
 * error, so one method covers both outcomes without touching the response itself.
 *
 * <p>Ordered at the {@link ServerFilterPhase#METRICS} phase, ahead of
 * {@link ServerFilterPhase#SECURITY}, so a request the security filter rejects before it reaches
 * a route still unwinds back through this filter and gets counted.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public final class HttpRequestCounterFilter implements Ordered {

  private final HttpRequestCounter counter;

  public HttpRequestCounterFilter(HttpRequestCounter counter) {
    this.counter = counter;
  }

  @Override
  public int getOrder() {
    return ServerFilterPhase.METRICS.order();
  }

  @ResponseFilter
  void countOutcome(@Nullable HttpResponse<?> response, @Nullable Throwable failure) {
    counter.recordRequest();
    if (failure != null || (response != null && response.getStatus().getCode() >= 400)) {
      counter.recordError();
    }
  }
}
