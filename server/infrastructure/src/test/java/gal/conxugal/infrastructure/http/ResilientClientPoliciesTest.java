package gal.conxugal.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.github.resilience4j.core.functions.Either;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * How long the retry policy waits. The parsing of {@code Retry-After} itself is {@link
 * RetryAfterTest}'s subject; what matters here is that a parsed value actually displaces the
 * backoff curve, and that one beyond the ceiling aborts instead of being waited out.
 */
@ExtendWith(MockitoExtension.class)
class ResilientClientPoliciesTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);
  private static final Duration BASE_DELAY = Duration.ofSeconds(2);
  private static final Duration MAXIMUM_WAIT = Duration.ofSeconds(30);

  @Mock private HttpResponse<?> response;

  private BiFunction<Integer, Either<Throwable, Object>, Long> waits() {
    return ResilientClientPolicies.retry(3, BASE_DELAY, MAXIMUM_WAIT, FIXED_CLOCK)
        .getIntervalBiFunction();
  }

  @Test
  void honours_retry_after_in_the_delay_seconds_form() {
    when(response.header(HttpHeaders.RETRY_AFTER)).thenReturn("12");

    Long wait = waits().apply(1, Either.left(responseException()));

    assertThat(wait).isEqualTo(Duration.ofSeconds(12).toMillis());
  }

  @Test
  void honours_retry_after_in_the_http_date_form() {
    ZonedDateTime retryAt = ZonedDateTime.now(FIXED_CLOCK).plusSeconds(20);
    when(response.header(HttpHeaders.RETRY_AFTER))
        .thenReturn(retryAt.format(DateTimeFormatter.RFC_1123_DATE_TIME));

    Long wait = waits().apply(1, Either.left(responseException()));

    assertThat(wait).isEqualTo(Duration.ofSeconds(20).toMillis());
  }

  @Test
  void aborts_when_retry_after_exceeds_the_maximum_wait() {
    when(response.header(HttpHeaders.RETRY_AFTER))
        .thenReturn(String.valueOf(MAXIMUM_WAIT.plusSeconds(1).toSeconds()));

    assertThatThrownBy(() -> waits().apply(1, Either.left(responseException())))
        .isInstanceOf(RetryAfterExceedsMaximumWaitException.class);
  }

  @Test
  void falls_back_to_jittered_backoff_without_the_header() {
    when(response.header(HttpHeaders.RETRY_AFTER)).thenReturn(null);

    Long wait = waits().apply(1, Either.left(responseException()));

    assertThat(wait)
        .isBetween(
            (long) (BASE_DELAY.toMillis() * 0.5), (long) (BASE_DELAY.toMillis() * 1.5));
  }

  @Test
  void falls_back_to_jittered_backoff_for_failures_carrying_no_response() {
    Long wait = waits().apply(1, Either.left(new HttpClientException("connection refused")));

    assertThat(wait)
        .isBetween(
            (long) (BASE_DELAY.toMillis() * 0.5), (long) (BASE_DELAY.toMillis() * 1.5));
  }

  private HttpClientResponseException responseException() {
    lenient().when(response.code()).thenReturn(503);
    return new HttpClientResponseException("service unavailable", response);
  }
}
