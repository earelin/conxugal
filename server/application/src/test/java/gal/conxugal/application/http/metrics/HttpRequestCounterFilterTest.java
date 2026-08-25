package gal.conxugal.application.http.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.metrics.HttpRequestCounter;
import gal.conxugal.domain.metrics.RuntimeMetrics;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpRequestCounterFilterTest {

  private HttpRequestCounter counter;
  private HttpRequestCounterFilter filter;

  @BeforeEach
  void setUp() {
    counter = new HttpRequestCounter();
    filter = new HttpRequestCounterFilter(counter);
  }

  @Test
  void counts_successful_response_as_request_only() {
    filter.countOutcome(HttpResponse.ok(), null);

    assertThat(counter.snapshot()).isEqualTo(new RuntimeMetrics.Http(1, 0));
  }

  @Test
  void counts_client_error_response_as_request_only() {
    filter.countOutcome(HttpResponse.notFound(), null);

    assertThat(counter.snapshot()).isEqualTo(new RuntimeMetrics.Http(1, 0));
  }

  @Test
  void counts_server_error_response_as_both_request_and_error() {
    filter.countOutcome(HttpResponse.serverError(), null);

    assertThat(counter.snapshot()).isEqualTo(new RuntimeMetrics.Http(1, 1));
  }

  @Test
  void counts_thrown_failure_as_both_request_and_error() {
    filter.countOutcome(null, new IllegalStateException("the route blew up"));

    assertThat(counter.snapshot()).isEqualTo(new RuntimeMetrics.Http(1, 1));
  }

  @Test
  void counts_failure_that_already_became_server_error_response_only_once() {
    filter.countOutcome(HttpResponse.serverError(), new IllegalStateException("the route blew up"));

    assertThat(counter.snapshot()).isEqualTo(new RuntimeMetrics.Http(1, 1));
  }
}
