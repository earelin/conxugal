package gal.conxugal.application.http.metrics;

import gal.conxugal.domain.metrics.HttpRequestCounter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

/**
 * Feeds {@link HttpRequestCounter} for every request, including requests to the metrics stream
 * itself. This response-filter signature is invoked for both a normal response and a thrown
 * error, so one method covers both outcomes without touching the response itself.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public final class HttpRequestCounterFilter {

  private final HttpRequestCounter counter;

  public HttpRequestCounterFilter(HttpRequestCounter counter) {
    this.counter = counter;
  }

  @ResponseFilter
  void countOutcome(@Nullable HttpResponse<?> response, @Nullable Throwable failure) {
    counter.recordRequest();
    if (failure != null || (response != null && response.getStatus().getCode() >= 400)) {
      counter.recordError();
    }
  }
}
