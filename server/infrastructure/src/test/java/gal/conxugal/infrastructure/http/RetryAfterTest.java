package gal.conxugal.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetryAfterTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);

  @Mock private HttpResponse<?> response;

  @Test
  void parses_the_delay_seconds_form() {
    when(response.header(HttpHeaders.RETRY_AFTER)).thenReturn("120");

    Optional<Duration> wait = RetryAfter.parse(response, FIXED_CLOCK);

    assertThat(wait).contains(Duration.ofMinutes(2));
  }

  @Test
  void parses_the_http_date_form_relative_to_the_given_clock() {
    ZonedDateTime retryAt = ZonedDateTime.now(FIXED_CLOCK).plusSeconds(30);
    when(response.header(HttpHeaders.RETRY_AFTER))
        .thenReturn(retryAt.format(DateTimeFormatter.RFC_1123_DATE_TIME));

    Optional<Duration> wait = RetryAfter.parse(response, FIXED_CLOCK);

    assertThat(wait).contains(Duration.ofSeconds(30));
  }

  @Test
  void past_http_date_clamps_to_zero_instead_of_negative() {
    ZonedDateTime retryAt = ZonedDateTime.now(FIXED_CLOCK).minusSeconds(30);
    when(response.header(HttpHeaders.RETRY_AFTER))
        .thenReturn(retryAt.format(DateTimeFormatter.RFC_1123_DATE_TIME));

    Optional<Duration> wait = RetryAfter.parse(response, FIXED_CLOCK);

    assertThat(wait).contains(Duration.ZERO);
  }

  @Test
  void returns_empty_when_the_header_is_absent() {
    when(response.header(HttpHeaders.RETRY_AFTER)).thenReturn(null);

    Optional<Duration> wait = RetryAfter.parse(response, FIXED_CLOCK);

    assertThat(wait).isEmpty();
  }

  @Test
  void returns_empty_when_the_header_is_unparseable_in_both_forms() {
    when(response.header(HttpHeaders.RETRY_AFTER)).thenReturn("not-a-valid-value");

    Optional<Duration> wait = RetryAfter.parse(response, FIXED_CLOCK);

    assertThat(wait).isEmpty();
  }
}
