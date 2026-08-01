package gal.conxugal.domain.organo.taxonomia;

import java.util.UUID;

/**
 * Thrown by {@link DeleteTermo} when the term still has child terms. Deleting it would take a
 * whole subtree with it, so the admin moves or removes the children first.
 */
public class TermoHasChildrenException extends RuntimeException {

  private final UUID termoId;

  public TermoHasChildrenException(UUID termoId) {
    super("Cannot delete term %s while it has child terms".formatted(termoId));
    this.termoId = termoId;
  }

  public UUID getTermoId() {
    return termoId;
  }
}
