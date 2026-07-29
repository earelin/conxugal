package gal.conxugal.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
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
        .thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("retry-success", delegate, fastSettings(3));

    String result =
        client.exchange(
            HttpRequest.GET("/organos"), ResilientHttpClientTest::mapToBody, ok -> true);

    assertThat(result).isEqualTo("ok");
    verify(delegate, times(3)).exchange(any(HttpRequest.class), eq(byte[].class));
  }

  @Test
  void permanent_status_is_not_retried() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Not Found", HttpResponse.notFound()));
    ResilientHttpClient client = new ResilientHttpClient("not-found", delegate, fastSettings(3));

    assertThatThrownBy(() -> exchangeOk(client))
        .isInstanceOf(HttpClientResponseException.class);
    verify(delegate, times(1)).exchange(any(HttpRequest.class), eq(byte[].class));
  }

  @Test
  void honors_retry_after_in_delay_seconds_form() {
    HttpResponse<?> tooManyRequests =
        HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "1");
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Too Many Requests", tooManyRequests))
        .thenReturn(okResponse("ok"));
    ResilientHttpClient client =
        new ResilientHttpClient("retry-after-seconds", delegate, fastSettings(2));

    Instant start = Instant.now();
    String result = exchangeOk(client);
    Duration elapsed = Duration.between(start, Instant.now());

    assertThat(result).isEqualTo("ok");
    assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void honors_retry_after_in_http_date_form() {
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

    Instant start = Instant.now();
    String result = exchangeOk(client);
    Duration elapsed = Duration.between(start, Instant.now());

    assertThat(result).isEqualTo("ok");
    assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void retry_after_beyond_the_configured_maximum_aborts_the_call() {
    ResilientHttpClientSettings settings =
        new FixedResilientHttpClientSettings(
            "https://example.test",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofMillis(10),
            Duration.ofSeconds(5),
            1,
            3,
            Duration.ofMillis(10),
            Duration.ofSeconds(1),
            50,
            Duration.ofSeconds(5));
    HttpResponse<?> tooManyRequests =
        HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600");
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Too Many Requests", tooManyRequests));
    ResilientHttpClient client = new ResilientHttpClient("retry-after-clamp", delegate, settings);

    assertThatThrownBy(() -> exchangeOk(client))
        .isInstanceOf(RetryAfterExceedsMaximumWaitException.class);
    verify(delegate, times(1)).exchange(any(HttpRequest.class), eq(byte[].class));
  }

  @Test
  void refused_permit_surfaces_as_http_client_exception_and_is_not_retried() {
    ResilientHttpClientSettings settings =
        new FixedResilientHttpClientSettings(
            "https://example.test",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofHours(1),
            Duration.ZERO,
            1,
            3,
            Duration.ofMillis(10),
            Duration.ofSeconds(5),
            50,
            Duration.ofSeconds(5));
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class))).thenReturn(okResponse("ok"));
    ResilientHttpClient client = new ResilientHttpClient("permit-refused", delegate, settings);
    exchangeOk(client);

    assertThatThrownBy(() -> exchangeOk(client))
        .isInstanceOf(HttpClientException.class)
        .hasCauseInstanceOf(RequestNotPermitted.class);
    verify(delegate, times(1)).exchange(any(HttpRequest.class), eq(byte[].class));
  }

  @Test
  void an_open_circuit_surfaces_as_http_client_exception_and_is_not_retried() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(serviceUnavailable());
    ResilientHttpClient client =
        new ResilientHttpClient("breaker-opens", delegate, fastBreakerSettings());

    for (int i = 0; i < ResilientHttpClient.BREAKER_MINIMUM_NUMBER_OF_CALLS; i++) {
      assertThatThrownBy(() -> exchangeOk(client)).isInstanceOf(HttpClientResponseException.class);
    }
    verify(delegate, times(ResilientHttpClient.BREAKER_MINIMUM_NUMBER_OF_CALLS))
        .exchange(any(HttpRequest.class), eq(byte[].class));

    assertThatThrownBy(() -> exchangeOk(client))
        .isInstanceOf(HttpClientException.class)
        .hasCauseInstanceOf(CallNotPermittedException.class);
    verify(delegate, times(ResilientHttpClient.BREAKER_MINIMUM_NUMBER_OF_CALLS))
        .exchange(any(HttpRequest.class), eq(byte[].class));
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

    for (int i = 0; i < ResilientHttpClient.BREAKER_MINIMUM_NUMBER_OF_CALLS; i++) {
      assertThatThrownBy(() -> exchangeOk(client)).isInstanceOf(HttpClientResponseException.class);
    }

    assertThat(exchangeOk(client)).isEqualTo("ok");
  }

  @Test
  void non_standard_status_code_is_classified_without_crashing() {
    HttpResponse<?> unknownStatus = HttpResponse.status(520, "Unknown Error");
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Unknown Error", unknownStatus));
    ResilientHttpClient client = new ResilientHttpClient("non-standard", delegate, fastSettings(3));

    assertThatThrownBy(() -> exchangeOk(client)).isInstanceOf(HttpClientResponseException.class);
    verify(delegate, times(1)).exchange(any(HttpRequest.class), eq(byte[].class));
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
    verify(delegate, times(0)).exchange(any(HttpRequest.class), eq(byte[].class));
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
        .thenReturn(okResponse("blocked"));
    ResilientHttpClient client =
        new ResilientHttpClient("acceptability", delegate, fastSettings(3));

    assertThatThrownBy(() -> exchangeRejecting(client))
        .isInstanceOf(UnacceptableResponseException.class);
    verify(delegate, times(1)).exchange(any(HttpRequest.class), eq(byte[].class));
  }

  @Test
  void repeated_unacceptable_responses_open_the_circuit_breaker() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenReturn(okResponse("blocked"));
    ResilientHttpClient client =
        new ResilientHttpClient("acceptability-breaker", delegate, fastBreakerSettings());

    for (int i = 0; i < ResilientHttpClient.BREAKER_MINIMUM_NUMBER_OF_CALLS; i++) {
      assertThatThrownBy(() -> exchangeRejecting(client))
          .isInstanceOf(UnacceptableResponseException.class);
    }

    assertThatThrownBy(() -> exchangeRejecting(client))
        .isInstanceOf(HttpClientException.class)
        .hasCauseInstanceOf(CallNotPermittedException.class);
    verify(delegate, times(ResilientHttpClient.BREAKER_MINIMUM_NUMBER_OF_CALLS))
        .exchange(any(HttpRequest.class), eq(byte[].class));
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
    verify(delegate, times(1)).exchange(any(HttpRequest.class), eq(byte[].class));
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
    verify(delegate, times(2)).exchange(any(HttpRequest.class), eq(byte[].class));
  }

  @Test
  void every_outgoing_request_carries_the_identifying_user_agent() {
    when(delegate.exchange(any(HttpRequest.class), eq(byte[].class))).thenReturn(okResponse("ok"));
    ResilientHttpClient client = new ResilientHttpClient("user-agent", delegate, fastSettings(1));
    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

    exchangeOk(client);

    verify(delegate).exchange(requestCaptor.capture(), eq(byte[].class));
    assertThat(requestCaptor.getValue().getHeaders().get(HttpHeaders.USER_AGENT))
        .isEqualTo(ResilientHttpClient.USER_AGENT_VALUE);
  }

  private static String exchangeOk(ResilientHttpClient client) {
    return client.exchange(
        HttpRequest.GET("/organos"), ResilientHttpClientTest::mapToBody, ok -> true);
  }

  private static String exchangeRejecting(ResilientHttpClient client) {
    return client.exchange(
        HttpRequest.GET("/organos"), ResilientHttpClientTest::mapToBody, rejected -> false);
  }

  private static HttpClientResponseException serviceUnavailable() {
    return new HttpClientResponseException(
        "Service Unavailable", HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE));
  }

  private static HttpResponse<byte[]> okResponse(String body) {
    return HttpResponse.ok(body.getBytes(StandardCharsets.UTF_8));
  }

  private static String mapToBody(HttpResponse<byte[]> response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  private static ResilientHttpClientSettings fastSettings(int retryMaxAttempts) {
    return new FixedResilientHttpClientSettings(
        "https://example.test",
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        Duration.ofMillis(10),
        Duration.ofSeconds(5),
        1,
        retryMaxAttempts,
        Duration.ofMillis(5),
        Duration.ofSeconds(5),
        50,
        Duration.ofSeconds(5));
  }

  private static ResilientHttpClientSettings fastBreakerSettings() {
    return new FixedResilientHttpClientSettings(
        "https://example.test",
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        Duration.ofMillis(1),
        Duration.ofSeconds(1),
        1,
        1,
        Duration.ofMillis(5),
        Duration.ofSeconds(5),
        50,
        Duration.ofMinutes(10));
  }
}
