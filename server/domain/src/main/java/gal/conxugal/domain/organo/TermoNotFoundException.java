package gal.conxugal.domain.organo;

import java.util.UUID;

/**
 * Thrown when an operation names a term id that doesn't exist — creating under an unknown
 * parent, renaming, moving or deleting an unknown term, or placing an Órgano in one.
 */
public class TermoNotFoundException extends RuntimeException {

  private final UUID termoId;

  public TermoNotFoundException(UUID termoId) {
    super(messageFor(termoId));
    this.termoId = termoId;
  }

  /**
   * The refusal as the {@code infrastructure} adapter raises it, translating a foreign key
   * that references {@code termo} so that a write racing a delete is refused the same way a
   * checked one is.
   */
  public TermoNotFoundException(UUID termoId, Throwable cause) {
    super(messageFor(termoId), cause);
    this.termoId = termoId;
  }

  public UUID getTermoId() {
    return termoId;
  }

  private static String messageFor(UUID termoId) {
    return "No term exists with id: %s".formatted(termoId);
  }
}
