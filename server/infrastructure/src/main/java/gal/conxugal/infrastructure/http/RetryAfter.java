package gal.conxugal.infrastructure.http;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Parses a {@code Retry-After} header in either form RFC 9110 permits: a non-negative integer
 * number of seconds, or an HTTP-date. A date-form header met by integer parsing alone would throw
 * from inside the retry machinery, so both forms are tried explicitly.
 */
final class RetryAfter {

  private RetryAfter() {}

  static Optional<Duration> parse(HttpResponse<?> response, Clock clock) {
    String value = response.header(HttpHeaders.RETRY_AFTER);
    if (value == null) {
      return Optional.empty();
    }
    return parseDelaySeconds(value).or(() -> parseHttpDate(value, clock));
  }

  private static Optional<Duration> parseDelaySeconds(String value) {
    try {
      long seconds = Long.parseLong(value.trim());
      return Optional.of(Duration.ofSeconds(Math.max(seconds, 0)));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static Optional<Duration> parseHttpDate(String value, Clock clock) {
    try {
      ZonedDateTime when = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
      Duration wait = Duration.between(Instant.now(clock), when.toInstant());
      return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
