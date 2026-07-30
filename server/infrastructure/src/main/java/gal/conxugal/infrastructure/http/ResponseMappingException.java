package gal.conxugal.infrastructure.http;

import io.micronaut.http.client.exceptions.HttpClientException;

/**
 * Thrown when a caller's own response mapper fails on a response the exchange itself delivered.
 * That is a defect in the caller's parsing, not a signal about the source's health, so it is
 * neither retried nor counted toward the circuit breaker's failure rate — otherwise a mapper
 * broken by a benign markup change would open the circuit against a source answering {@code 200}
 * every time.
 */
public class ResponseMappingException extends HttpClientException {

  public ResponseMappingException(String message, Throwable cause) {
    super(message, cause);
  }
}
