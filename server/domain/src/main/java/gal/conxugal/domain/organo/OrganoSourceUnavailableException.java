package gal.conxugal.domain.organo;

/**
 * Thrown by an {@link OrganoSource} adapter when the source is unreachable or its response is
 * unusable — including an empty or implausibly small list — rather than let a reconciliation use
 * case receive a partial or empty success.
 */
public class OrganoSourceUnavailableException extends RuntimeException {

  public OrganoSourceUnavailableException(String message) {
    super(message);
  }

  public OrganoSourceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
