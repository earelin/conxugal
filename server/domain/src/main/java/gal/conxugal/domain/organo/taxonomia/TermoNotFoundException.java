package gal.conxugal.domain.organo.taxonomia;

import java.util.UUID;

/**
 * Thrown when an operation names a term id that doesn't exist — whether as the term being
 * changed or as the parent it is being placed under. Classification reuses this type rather
 * than declaring a second unknown-term one, so an unknown term maps to a single problem type.
 */
public class TermoNotFoundException extends RuntimeException {

  private final UUID termoId;

  public TermoNotFoundException(UUID termoId) {
    super("No term exists with id: " + termoId);
    this.termoId = termoId;
  }

  public UUID getTermoId() {
    return termoId;
  }
}
