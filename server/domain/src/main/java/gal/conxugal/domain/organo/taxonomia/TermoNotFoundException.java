package gal.conxugal.domain.organo.taxonomia;

/**
 * Thrown when an operation names a term id that doesn't exist — whether as the term being
 * changed or as the parent it is being placed under. Classification reuses this type rather
 * than declaring a second unknown-term one, so an unknown term maps to a single problem type.
 */
public class TermoNotFoundException extends RuntimeException {

  private final TermoId termoId;

  public TermoNotFoundException(TermoId termoId) {
    super("No term exists with id: %s".formatted(termoId));
    this.termoId = termoId;
  }

  public TermoId getTermoId() {
    return termoId;
  }
}
