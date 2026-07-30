package gal.conxugal.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.core.exception.AcquirePermissionCancelledException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResilientHttpClientTest {

  @Mock private BlockingHttpClient delegate;

  @Test
  void retries_transient_failures_up_to_the_configured_limit_then_returns_the_success() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(serviceUnavailable())
        .thenThrow(serviceUnavailable())
        .thenReturn(okResponse("ok"))
        .thenThrow(new AssertionError("attempted more than the configured retry limit"));
    ResilientHttpClient client =
        new ResilientHttpClient("retry-success", delegate, fastSettings(3));

    String result =
        client.exchange(
            HttpRequest.GET("/organos"), ResilientHttpClientTest::mapToBody, ok -> true);

    assertThat(result).isEqualTo("ok");
  }

  @Test
  void exhausting_every_attempt_surfaces_the_last_transient_failure() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(serviceUnavailable("first"))
        .thenThrow(serviceUnavailable("second"))
        .thenThrow(serviceUnavailable("last"))
        .thenThrow(new AssertionError("attempted more than the configured retry limit"));
    ResilientHttpClient client =
        new ResilientHttpClient("retry-exhausted", delegate, fastSettings(3));

    assertThatThrownBy(() -> exchangeOk(client))
        .isInstanceOf(HttpClientResponseException.class)
        .hasMessage("last");
  }

  @Test
  void permanent_status_is_not_retried() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Not Found", HttpResponse.notFound()))
        .thenThrow(new AssertionError("retried a permanent failure"));
    ResilientHttpClient client = new ResilientHttpClient("not-found", delegate, fastSettings(3));

    assertThatThrownBy(() -> exchangeOk(client))
        .isInstanceOf(HttpClientResponseException.class);
  }

  @Test
  void delay_seconds_retry_after_is_honoured_and_the_call_still_succeeds() {
    HttpResponse<?> tooManyRequests =
        HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "1");
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Too Many Requests", tooManyRequests))
        .thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("retry-after-seconds", delegate, fastSettings(2));

    assertThat(exchangeOk(client)).isEqualTo("ok");
  }

  @Test
  void date_form_retry_after_is_honoured_rather_than_throwing_from_the_retry_machinery() {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);
    String retryAtHeader =
        ZonedDateTime.now(fixedClock).plusSeconds(1).format(DateTimeFormatter.RFC_1123_DATE_TIME);
    HttpResponse<?> serviceUnavailable =
        HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, retryAtHeader);
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Service Unavailable", serviceUnavailable))
        .thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("retry-after-date", delegate, fastSettings(2), fixedClock);

    assertThat(exchangeOk(client)).isEqualTo("ok");
  }

  @Test
  void retry_after_beyond_the_configured_maximum_aborts_the_call() {
    ResilientHttpClientSettings settings =
        TestResilientHttpClientSettings.builder()
            .retryAfterMaximumWait(Duration.ofSeconds(1))
            .build();
    HttpResponse<?> tooManyRequests =
        HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600");
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Too Many Requests", tooManyRequests))
        .thenThrow(new AssertionError("waited out a Retry-After beyond the configured maximum"));
    ResilientHttpClient client = new ResilientHttpClient("retry-after-clamp", delegate, settings);

    assertThatThrownBy(() -> exchangeOk(client))
        .isInstanceOf(RetryAfterExceedsMaximumWaitException.class);
  }

  @Test
  void retry_after_exactly_at_the_configured_maximum_is_honoured_rather_than_aborted() {
    ResilientHttpClientSettings settings =
        TestResilientHttpClientSettings.builder()
            .retryAfterMaximumWait(Duration.ofSeconds(1))
            .build();
    HttpResponse<?> tooManyRequests =
        HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "1");
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Too Many Requests", tooManyRequests))
        .thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("retry-after-boundary", delegate, settings);

    assertThat(exchangeOk(client)).isEqualTo("ok");
  }

  @Test
  void refused_permit_surfaces_as_http_client_exception_and_is_not_retried() {
    ResilientHttpClientSettings settings =
        TestResilientHttpClientSettings.builder()
            .rateLimiterRefreshPeriod(Duration.ofHours(1))
            .maximumPermitWait(Duration.ZERO)
            .build();
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class))).thenReturn(okResponse("ok"));
    ResilientHttpClient client = new ResilientHttpClient("permit-refused", delegate, settings);
    exchangeOk(client);

    assertThatThrownBy(() -> exchangeOk(client))
        .isInstanceOf(HttpClientException.class)
        .hasCauseInstanceOf(RequestNotPermitted.class);
  }

  @Test
  void an_open_circuit_surfaces_as_http_client_exception_and_is_not_retried() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(serviceUnavailable());
    ResilientHttpClient client =
        new ResilientHttpClient("breaker-opens", delegate, fastBreakerSettings());

    for (int i = 0; i < ResilientPolicies.BREAKER_MINIMUM_NUMBER_OF_CALLS; i++) {
      assertThatThrownBy(() -> exchangeOk(client)).isInstanceOf(HttpClientResponseException.class);
    }

    assertThatThrownBy(() -> exchangeOk(client))
        .isInstanceOf(HttpClientException.class)
        .hasCauseInstanceOf(CallNotPermittedException.class);
  }

  @Test
  void client_side_status_does_not_count_as_breaker_failure() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Not Found", HttpResponse.notFound()))
        .thenThrow(new HttpClientResponseException("Not Found", HttpResponse.notFound()))
        .thenThrow(new HttpClientResponseException("Not Found", HttpResponse.notFound()))
        .thenThrow(new HttpClientResponseException("Not Found", HttpResponse.notFound()))
        .thenThrow(new HttpClientResponseException("Not Found", HttpResponse.notFound()))
        .thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("client-error-breaker", delegate, fastBreakerSettings());

    for (int i = 0; i < ResilientPolicies.BREAKER_MINIMUM_NUMBER_OF_CALLS; i++) {
      assertThatThrownBy(() -> exchangeOk(client)).isInstanceOf(HttpClientResponseException.class);
    }

    assertThat(exchangeOk(client)).isEqualTo("ok");
  }

  @Test
  void non_standard_status_code_is_classified_without_crashing() {
    HttpResponse<?> unknownStatus = HttpResponse.status(520, "Unknown Error");
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Unknown Error", unknownStatus))
        .thenThrow(new AssertionError("retried a non-retryable status"));
    ResilientHttpClient client = new ResilientHttpClient("non-standard", delegate, fastSettings(3));

    assertThatThrownBy(() -> exchangeOk(client)).isInstanceOf(HttpClientResponseException.class);
  }

  @Test
  void an_interrupted_permit_wait_surfaces_as_http_client_exception_and_is_not_retried() {
    ResilientHttpClient client = new ResilientHttpClient("interrupted", delegate, fastSettings(3));
    Thread.currentThread().interrupt();

    try {
      assertThatThrownBy(() -> exchangeOk(client))
          .isInstanceOf(HttpClientException.class)
          .hasCauseInstanceOf(AcquirePermissionCancelledException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void reusing_the_same_request_does_not_duplicate_the_user_agent_header() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class))).thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("user-agent-reuse", delegate, fastSettings(1));
    MutableHttpRequest<?> request =
        HttpRequest.GET("/organos").header(HttpHeaders.USER_AGENT, "caller-ua");

    client.exchange(request, ResilientHttpClientTest::mapToBody, ok -> true);
    client.exchange(request, ResilientHttpClientTest::mapToBody, ok -> true);

    assertThat(request.getHeaders().getAll(HttpHeaders.USER_AGENT))
        .containsExactly(ResilientHttpClient.USER_AGENT_VALUE);
  }

  @Test
  void unacceptable_response_is_recorded_as_breaker_failure_and_is_not_retried() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenReturn(okResponse("blocked"))
        .thenThrow(new AssertionError("retried an unacceptable response"));
    ResilientHttpClient client =
        new ResilientHttpClient("acceptability", delegate, fastSettings(3));

    assertThatThrownBy(() -> exchangeRejecting(client))
        .isInstanceOf(UnacceptableResponseException.class);
  }

  @Test
  void repeated_unacceptable_responses_open_the_circuit_breaker() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenReturn(okResponse("blocked"));
    ResilientHttpClient client =
        new ResilientHttpClient("acceptability-breaker", delegate, fastBreakerSettings());

    for (int i = 0; i < ResilientPolicies.BREAKER_MINIMUM_NUMBER_OF_CALLS; i++) {
      assertThatThrownBy(() -> exchangeRejecting(client))
          .isInstanceOf(UnacceptableResponseException.class);
    }

    assertThatThrownBy(() -> exchangeRejecting(client))
        .isInstanceOf(HttpClientException.class)
        .hasCauseInstanceOf(CallNotPermittedException.class);
  }

  @Test
  void failing_response_mapper_surfaces_as_mapping_failure_and_is_not_retried() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenReturn(okResponse("ok"))
        .thenThrow(new AssertionError("retried a caller's own mapper defect"));
    ResilientHttpClient client =
        new ResilientHttpClient("mapper-defect", delegate, fastSettings(3));

    assertThatThrownBy(() -> exchangeWithFailingMapper(client))
        .isInstanceOf(ResponseMappingException.class)
        .hasCauseInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void repeated_mapper_defects_leave_the_circuit_closed() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class))).thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("mapper-defect-breaker", delegate, fastBreakerSettings());

    for (int i = 0; i < ResilientPolicies.BREAKER_MINIMUM_NUMBER_OF_CALLS; i++) {
      assertThatThrownBy(() -> exchangeWithFailingMapper(client))
          .isInstanceOf(ResponseMappingException.class);
    }

    assertThat(exchangeOk(client)).isEqualTo("ok");
  }

  @Test
  void non_idempotent_post_is_not_retried_by_default() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(serviceUnavailable())
        .thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("idempotency-default", delegate, fastSettings(3));

    assertThatThrownBy(
            () ->
                client.exchange(
                    HttpRequest.POST("/organos", "{}"), ResilientHttpClientTest::mapToBody,
                    ok -> true))
        .isInstanceOf(HttpClientResponseException.class);
  }

  @Test
  void post_declared_idempotent_is_retried() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(serviceUnavailable())
        .thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("idempotency-declared", delegate, fastSettings(3));

    String result =
        client.exchange(
            HttpRequest.POST("/organos", "{}"), ResilientHttpClientTest::mapToBody, ok -> true,
            true);

    assertThat(result).isEqualTo("ok");
  }

  @Test
  void every_outgoing_request_carries_the_identifying_user_agent() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class))).thenReturn(okResponse("ok"));
    ResilientHttpClient client = new ResilientHttpClient("user-agent", delegate, fastSettings(1));
    MutableHttpRequest<?> request = HttpRequest.GET("/organos");

    client.exchange(request, ResilientHttpClientTest::mapToBody, ok -> true);

    assertThat(request.getHeaders().get(HttpHeaders.USER_AGENT))
        .isEqualTo(ResilientHttpClient.USER_AGENT_VALUE);
  }

  @Test
  void blank_base_url_is_rejected_when_the_client_is_built() {
    ResilientHttpClientSettings settings =
        TestResilientHttpClientSettings.builder().baseUrl("  ").build();

    assertThatThrownBy(() -> new ResilientHttpClient("blank-base-url", delegate, settings))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("baseUrl");
  }

  @Test
  void zero_retry_base_delay_is_rejected_when_the_client_is_built() {
    ResilientHttpClientSettings settings =
        TestResilientHttpClientSettings.builder().retryBaseDelay(Duration.ZERO).build();

    assertThatThrownBy(() -> new ResilientHttpClient("zero-backoff", delegate, settings))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retryBaseDelay");
  }

  @Test
  void failure_rate_threshold_above_one_hundred_is_rejected_when_the_client_is_built() {
    ResilientHttpClientSettings settings =
        TestResilientHttpClientSettings.builder().breakerFailureRateThreshold(101).build();

    assertThatThrownBy(() -> new ResilientHttpClient("bad-threshold", delegate, settings))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("breakerFailureRateThreshold");
  }

  @Test
  void retry_limit_below_one_is_rejected_when_the_client_is_built() {
    ResilientHttpClientSettings settings =
        TestResilientHttpClientSettings.builder().retryMaxAttempts(0).build();

    assertThatThrownBy(() -> new ResilientHttpClient("no-attempts", delegate, settings))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retryMaxAttempts");
  }

  private static String exchangeOk(ResilientHttpClient client) {
    return client.exchange(
        HttpRequest.GET("/organos"), ResilientHttpClientTest::mapToBody, ok -> true);
  }

  private static String exchangeRejecting(ResilientHttpClient client) {
    return client.exchange(
        HttpRequest.GET("/organos"), ResilientHttpClientTest::mapToBody, rejected -> false);
  }

  private static String exchangeWithFailingMapper(ResilientHttpClient client) {
    return client.exchange(
        HttpRequest.GET("/organos"),
        response -> {
          throw new IndexOutOfBoundsException("no such element in the parsed document");
        },
        ok -> true);
  }

  private static HttpClientResponseException serviceUnavailable() {
    return serviceUnavailable("Service Unavailable");
  }

  private static HttpClientResponseException serviceUnavailable(String message) {
    return new HttpClientResponseException(
        message, HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE));
  }

  private static HttpResponse<byte[]> okResponse(String body) {
    return HttpResponse.ok(body.getBytes(StandardCharsets.UTF_8));
  }

  private static String mapToBody(HttpResponse<byte[]> response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  private static ResilientHttpClientSettings fastSettings(int retryMaxAttempts) {
    return TestResilientHttpClientSettings.builder().retryMaxAttempts(retryMaxAttempts).build();
  }

  /** One breaker call per logical call (no retry), and an open circuit that stays open. */
  private static ResilientHttpClientSettings fastBreakerSettings() {
    return TestResilientHttpClientSettings.builder()
        .rateLimiterRefreshPeriod(Duration.ofMillis(1))
        .maximumPermitWait(Duration.ofSeconds(1))
        .retryMaxAttempts(1)
        .breakerOpenStateDuration(Duration.ofMinutes(10))
        .build();
  }
}
