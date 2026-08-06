package gal.conxugal.domain.contrato;

/**
 * Thrown by a {@link ContratoMenorSource} adapter when the source is unreachable or its response
 * cannot be read as the shape it documents, rather than let an import mistake a failure for a
 * window that genuinely held nothing.
 *
 * <p>A slice asking for more than the source allows is not this: that is a bug of ours, refused
 * before a request is issued, and it surfaces as an {@link IllegalArgumentException}.
 */
public class ContratoMenorSourceUnavailableException extends RuntimeException {

  public ContratoMenorSourceUnavailableException(String message) {
    super(message);
  }

  public ContratoMenorSourceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
