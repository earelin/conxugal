package gal.conxugal.infrastructure.http;

import io.micronaut.http.client.exceptions.HttpClientException;

/**
 * Thrown when a caller's acceptability check rejects an otherwise successfully mapped response.
 * Recorded as a circuit-breaker failure but never retried — a source defending itself with a
 * {@code 200} block page is pointless to retry but essential to open the circuit against.
 */
public class UnacceptableResponseException extends HttpClientException {

  public UnacceptableResponseException(String message) {
    super(message);
  }
}
