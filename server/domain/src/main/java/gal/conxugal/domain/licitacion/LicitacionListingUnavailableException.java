package gal.conxugal.domain.licitacion;

/**
 * Thrown by a {@link LicitacionListingSource} adapter when the source is unreachable or its
 * response cannot be read as the shape it documents, rather than let a walk mistake a failure for
 * an Órgano that genuinely published nothing. An empty page ends a walk and a failed request must
 * not.
 *
 * <p>A page asking for more than the source allows is not this: that is a bug of ours, refused
 * before a request is issued, and it surfaces as an {@link IllegalArgumentException}.
 */
public class LicitacionListingUnavailableException extends RuntimeException {

  public LicitacionListingUnavailableException(String message) {
    super(message);
  }

  public LicitacionListingUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
