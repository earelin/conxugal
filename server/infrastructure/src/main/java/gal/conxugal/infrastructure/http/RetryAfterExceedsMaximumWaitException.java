package gal.conxugal.infrastructure.http;

import io.micronaut.http.client.exceptions.HttpClientException;

/**
 * Thrown when a response's {@code Retry-After} exceeds the configured maximum wait. The call
 * aborts rather than waiting beyond that ceiling — a {@code Retry-After: 3600} met repeatedly would
 * otherwise turn one maintenance window into a run that looks alive for hours and returns nothing.
 */
public class RetryAfterExceedsMaximumWaitException extends HttpClientException {

  public RetryAfterExceedsMaximumWaitException(String message, Throwable cause) {
    super(message, cause);
  }
}
